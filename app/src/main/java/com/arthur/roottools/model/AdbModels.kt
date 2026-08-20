package com.arthur.roottools.model

enum class AdbEndpointType(val displayName: String) {
    TAILSCALE("Tailscale"),
    LOCAL_NETWORK("Local network"),
    NATIVE_WIRELESS("Native wireless"),
}

data class AdbEndpoint(
    val type: AdbEndpointType,
    val host: String,
    val port: Int,
    val recommended: Boolean = false,
) {
    val address: String get() = "$host:$port"
    val connectCommand: String get() = "adb connect $address"
}

data class AdbBootPolicy(
    val restoreRootTcp: Boolean = false,
    val restoreNativeWireless: Boolean = false,
)

data class AdbSnapshot(
    val rootAvailable: Boolean = false,
    val rootTcpPort: Int? = null,
    val rootTcpListening: Boolean = false,
    val nativeWirelessSupported: Boolean = false,
    val nativeWirelessQrSupported: Boolean = false,
    val nativeWirelessEnabled: Boolean = false,
    val nativeTlsPort: Int? = null,
    val usbDebuggingEnabled: Boolean = false,
    val usbTransportActive: Boolean = false,
    val tailscaleIpv4: String? = null,
    val localIpv4: String? = null,
    val localInterface: String? = null,
    val trustedHosts: List<String> = emptyList(),
    val bootPolicy: AdbBootPolicy = AdbBootPolicy(),
    val collectedAtMs: Long = 0L,
) {
    val rootTcpEnabled: Boolean get() = rootTcpPort != null && rootTcpPort > 0 && rootTcpListening

    val endpoints: List<AdbEndpoint>
        get() = buildList {
            val rootPort = rootTcpPort?.takeIf { rootTcpListening }
            if (rootPort != null) {
                tailscaleIpv4?.let {
                    add(AdbEndpoint(AdbEndpointType.TAILSCALE, it, rootPort, recommended = true))
                }
                localIpv4?.takeIf { it != tailscaleIpv4 }?.let {
                    add(AdbEndpoint(AdbEndpointType.LOCAL_NETWORK, it, rootPort))
                }
            }
            val tlsPort = nativeTlsPort?.takeIf { nativeWirelessEnabled }
            val local = localIpv4
            if (tlsPort != null && local != null) {
                add(AdbEndpoint(AdbEndpointType.NATIVE_WIRELESS, local, tlsPort))
            }
        }
}

data class AdbActionResult(
    val success: Boolean,
    val message: String,
    val snapshot: AdbSnapshot? = null,
)

