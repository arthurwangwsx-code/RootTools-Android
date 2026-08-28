package com.arthur.roottools.data

import com.arthur.roottools.model.ListeningPortInfo
import com.arthur.roottools.model.NetworkInterfaceInfo
import com.arthur.roottools.model.NetworkSnapshot
import com.arthur.roottools.model.PingResult
import com.arthur.roottools.root.RootShell

class NetworkRepository(private val shell: RootShell) {
    suspend fun read(): NetworkSnapshot {
        val result = shell.execute(READ_COMMAND, timeoutSeconds = 10)
        if (!result.success) return NetworkSnapshot()
        val sections = splitSections(result.output)
        val interfaces = parseInterfaces(sections["ADDR"].orEmpty())
        val connectivity = sections["CONNECTIVITY"].orEmpty()
        val transports = linkedSetOf<String>().apply {
            connectivity.forEach { line ->
                if (line.contains("Transports:") || line.contains("ni{")) {
                    if (line.contains("CELLULAR") || line.contains("MOBILE[")) add("CELLULAR")
                    if (line.contains("WIFI")) add("WIFI")
                    if (line.contains("VPN")) add("VPN")
                }
            }
        }
        val dns = connectivity.filter { it.contains("DnsAddresses:") }.flatMap { line ->
            DNS_REGEX.findAll(line).map { it.groupValues[1] }.toList()
        }.distinct()
        val routes = buildList {
            addAll(sections["ROUTE"].orEmpty().map(String::trim).filter(String::isNotBlank))
            connectivity.forEach { line ->
                ROUTE_BLOCK_REGEX.find(line)?.groupValues?.getOrNull(1)
                    ?.split(',')?.map(String::trim)?.filter(String::isNotBlank)?.let(::addAll)
            }
        }.distinct().take(24)
        val serviceState = sections["TELEPHONY"]?.firstOrNull().orEmpty()
        val carrier = Regex("mOperatorAlphaLong=([^,}]+)").find(serviceState)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it != "null" }
        val radio = Regex("getRilVoiceRadioTechnology=[0-9]+\\(([^)]+)\\)").find(serviceState)?.groupValues?.getOrNull(1)
            ?: Regex("accessNetworkTechnology=([A-Za-z0-9_]+)").find(serviceState)?.groupValues?.getOrNull(1)
        val adbPort = sections["ADB"]?.firstOrNull { it.startsWith("PORT=") }?.substringAfter('=')?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        val listening = parseListening(sections["LISTEN"].orEmpty())
        val tailscaleIpv4 = interfaces.firstOrNull { candidate ->
            (candidate.name == "tailscale0" || TUN_INTERFACE_REGEX.matches(candidate.name)) &&
                isTailnetIpv4(candidate.ipv4)
        }?.ipv4
        return NetworkSnapshot(
            interfaces = interfaces,
            transports = transports,
            dnsServers = dns,
            routes = routes,
            listeningPorts = listening,
            tailscaleIpv4 = tailscaleIpv4,
            wifiIpv4 = interfaces.firstOrNull { it.name == "wlan0" }?.ipv4,
            cellularIpv4 = interfaces.firstOrNull { it.name.startsWith("rmnet_data") }?.ipv4,
            carrierName = carrier,
            radioTechnology = radio,
            adbPort = adbPort,
            adbListening = adbPort != null && listening.any { it.port == adbPort },
        )
    }

    suspend fun ping(target: String): PingResult {
        val safe = target.trim().takeIf { HOST_REGEX.matches(it) && it.length <= 253 }
            ?: return PingResult(target, false, rawSummary = "目标格式无效")
        val result = shell.execute("ping -c 3 -W 2 $safe 2>&1", timeoutSeconds = 10)
        val packet = PACKET_REGEX.find(result.output)
        val transmitted = packet?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val received = packet?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
        val loss = packet?.groupValues?.getOrNull(3)?.toFloatOrNull() ?: if (result.success) 0f else 100f
        val avg = RTT_REGEX.find(result.output)?.groupValues?.getOrNull(1)?.split('/')?.getOrNull(1)?.toFloatOrNull()
        return PingResult(
            target = safe,
            success = result.success && received > 0,
            packetsTransmitted = transmitted,
            packetsReceived = received,
            packetLossPercent = loss,
            avgMs = avg,
            rawSummary = result.output.lines().takeLast(3).joinToString("\n").take(500),
        )
    }

    private fun parseInterfaces(lines: List<String>): List<NetworkInterfaceInfo> = lines.mapNotNull { line ->
        val match = ADDR_REGEX.find(line) ?: return@mapNotNull null
        NetworkInterfaceInfo(
            name = match.groupValues[1],
            ipv4 = match.groupValues[2],
            prefixLength = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null,
        )
    }.filterNot { it.name == "lo" }

    private fun parseListening(lines: List<String>): List<ListeningPortInfo> = lines.mapNotNull { line ->
        if (!line.contains("LISTEN")) return@mapNotNull null
        val local = line.trim().split(Regex("\\s+")).getOrNull(3) ?: return@mapNotNull null
        val port = local.substringAfterLast(':').toIntOrNull() ?: return@mapNotNull null
        ListeningPortInfo(address = local.substringBeforeLast(':').ifBlank { "*" }, port = port)
    }.distinctBy { it.address to it.port }.sortedBy { it.port }

    private fun isTailnetIpv4(value: String): Boolean {
        val octets = value.split('.').map { it.toIntOrNull() ?: return false }
        return octets.size == 4 && octets.all { it in 0..255 } && octets[0] == 100 && octets[1] in 64..127
    }

    private fun splitSections(raw: String): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableList<String>>()
        var current: String? = null
        raw.lineSequence().forEach { line ->
            if (line.startsWith("__") && line.endsWith("__")) {
                val section = line.removePrefix("__").removeSuffix("__")
                current = section
                result.getOrPut(section) { mutableListOf() }
            } else current?.let { result.getOrPut(it) { mutableListOf() }.add(line) }
        }
        return result
    }

    private companion object {
        val HOST_REGEX = Regex("[A-Za-z0-9][A-Za-z0-9._:-]*")
        val TUN_INTERFACE_REGEX = Regex("tun[0-9]+")
        val ADDR_REGEX = Regex("^[0-9]+:\\s+([^\\s]+)\\s+inet\\s+([0-9.]+)/([0-9]+)")
        val DNS_REGEX = Regex("/(?:([0-9a-fA-F:.]+))")
        val ROUTE_BLOCK_REGEX = Regex("Routes: \\[([^]]+)]")
        val PACKET_REGEX = Regex("([0-9]+) packets transmitted, ([0-9]+) received, ([0-9.]+)% packet loss")
        val RTT_REGEX = Regex("(?:rtt|round-trip) min/avg/max/(?:mdev|stddev) = ([0-9./]+) ms")
        val READ_COMMAND = """
            echo '__ADDR__'
            ip -4 -o addr show 2>/dev/null
            echo '__ROUTE__'
            ip route 2>/dev/null
            echo '__CONNECTIVITY__'
            dumpsys connectivity 2>/dev/null | grep -E 'NetworkAgentInfo\\{|InterfaceName:|DnsAddresses:|Routes:|Transports:' | head -n 120
            echo '__TELEPHONY__'
            dumpsys telephony.registry 2>/dev/null | grep -m 1 'mServiceState='
            echo '__LISTEN__'
            ss -ltn 2>/dev/null | head -n 80
            echo '__ADB__'
            echo PORT=${'$'}(getprop service.adb.tcp.port)
        """.trimIndent()
    }
}
