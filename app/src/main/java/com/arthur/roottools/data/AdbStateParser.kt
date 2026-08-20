package com.arthur.roottools.data

import com.arthur.roottools.model.AdbBootPolicy
import com.arthur.roottools.model.AdbSnapshot

internal object AdbStateParser {
    fun parse(raw: String, rootAvailable: Boolean, bootPolicy: AdbBootPolicy, collectedAtMs: Long): AdbSnapshot {
        val sections = splitSections(raw)
        val legacyPort = value(sections, "ROOT_TCP", "PORT")?.toIntOrNull()?.takeIf { it > 0 }
        val adbdPorts = sections["ADBD_PORTS"].orEmpty().mapNotNull { it.trim().toIntOrNull() }.distinct()
        val nativeEnabled = value(sections, "NATIVE", "ENABLED") == "1"
        val nativePort = adbdPorts.firstOrNull { it > 1024 && it != legacyPort }

        return AdbSnapshot(
            rootAvailable = rootAvailable,
            rootTcpPort = legacyPort,
            rootTcpListening = legacyPort != null && legacyPort in adbdPorts,
            nativeWirelessSupported = value(sections, "NATIVE", "SUPPORTED").equals("true", ignoreCase = true),
            nativeWirelessQrSupported = value(sections, "NATIVE", "QR").equals("true", ignoreCase = true),
            nativeWirelessEnabled = nativeEnabled,
            nativeTlsPort = if (nativeEnabled) nativePort else null,
            usbDebuggingEnabled = value(sections, "USB", "ADB_ENABLED") == "1",
            usbTransportActive = value(sections, "USB", "ACTIVE") == "1",
            tailscaleIpv4 = value(sections, "NETWORK", "TAILSCALE")?.ifBlank { null },
            localIpv4 = value(sections, "NETWORK", "LOCAL")?.ifBlank { null },
            localInterface = value(sections, "NETWORK", "IFACE")?.ifBlank { null },
            trustedHosts = sections["TRUSTED"].orEmpty().map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            bootPolicy = bootPolicy,
            collectedAtMs = collectedAtMs,
        )
    }

    private fun value(sections: Map<String, List<String>>, section: String, key: String): String? =
        sections[section]?.firstOrNull { it.startsWith("$key=") }?.substringAfter('=')?.trim()

    private fun splitSections(raw: String): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableList<String>>()
        var current: String? = null
        raw.lineSequence().forEach { line ->
            if (line.startsWith("__") && line.endsWith("__")) {
                val section = line.removePrefix("__").removeSuffix("__")
                current = section
                result.getOrPut(section) { mutableListOf() }
            } else {
                current?.let { result.getOrPut(it) { mutableListOf() }.add(line) }
            }
        }
        return result
    }
}

