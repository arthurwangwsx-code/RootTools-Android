package com.arthur.roottools.model

data class NetworkInterfaceInfo(
    val name: String,
    val ipv4: String,
    val prefixLength: Int,
)

data class ListeningPortInfo(
    val address: String,
    val port: Int,
)

data class NetworkSnapshot(
    val interfaces: List<NetworkInterfaceInfo> = emptyList(),
    val transports: Set<String> = emptySet(),
    val dnsServers: List<String> = emptyList(),
    val routes: List<String> = emptyList(),
    val listeningPorts: List<ListeningPortInfo> = emptyList(),
    val tailscaleIpv4: String? = null,
    val wifiIpv4: String? = null,
    val cellularIpv4: String? = null,
    val carrierName: String? = null,
    val radioTechnology: String? = null,
    val adbPort: Int? = null,
    val adbListening: Boolean = false,
) {
    val tailscaleActive: Boolean get() = tailscaleIpv4 != null && "VPN" in transports
    val primarySummary: String
        get() = buildList {
            if ("WIFI" in transports) add("Wi-Fi")
            if ("CELLULAR" in transports) add("Cellular")
            if ("VPN" in transports) add("VPN")
        }.joinToString(" + ").ifBlank { "Offline" }
}

data class PingResult(
    val target: String,
    val success: Boolean,
    val packetsTransmitted: Int = 0,
    val packetsReceived: Int = 0,
    val packetLossPercent: Float = 100f,
    val avgMs: Float? = null,
    val rawSummary: String = "",
)
