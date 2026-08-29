package com.aibox.backgroundserver.domain

data class RootStatus(
    val available: Boolean = false,
    val detail: String = "检测中",
)

data class PowerSettings(
    val screenOffWorkEnabled: Boolean = false,
    val restoreAfterBoot: Boolean = false,
    val screenOffWithoutLock: Boolean = false,
    val doubleTapToWake: Boolean? = null,
    val interactive: Boolean = true,
)

data class RuntimeMetrics(
    val running: Boolean = false,
    val startedAtMillis: Long? = null,
    val runtimeMillis: Long = 0L,
    val instantaneousWatts: Double? = null,
    val accumulatedWh: Double = 0.0,
    val cpuLoadPercent: Double? = null,
    val loadAverage1: Double? = null,
    val loadAverage5: Double? = null,
    val loadAverage15: Double? = null,
    val memoryUsedPercent: Double? = null,
    val totalRxBytes: Long? = null,
    val totalTxBytes: Long? = null,
    val temperatureCelsius: Double? = null,
    val batteryCharging: Boolean = false,
)

data class NetworkAddress(
    val interfaceName: String,
    val address: String,
    val ipv6: Boolean,
)

data class NetworkSnapshot(
    val addresses: List<NetworkAddress> = emptyList(),
    val wifiLikeIpv4: String? = null,
    val primaryInterface: String? = null,
    val primaryCidr: String? = null,
    val gateway: String? = null,
    val dnsServers: List<String> = emptyList(),
)

data class NetworkCapabilities(
    val tunAvailable: Boolean = false,
    val iptablesAvailable: Boolean = false,
    val kernelWireGuardAvailable: Boolean = false,
    val wireGuardToolsAvailable: Boolean = false,
    val ipv4ForwardingEnabled: Boolean = false,
    val recommendedBackend: String = "检测中",
    val detail: String = "",
)

enum class TunnelRuntimeState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR,
}

data class WireGuardServerState(
    val runtimeState: TunnelRuntimeState = TunnelRuntimeState.STOPPED,
    val backend: String = "Userspace GoBackend",
    val listenPort: Int = 51820,
    val tunnelAddress: String = "10.77.0.1/24",
    val peerAddress: String = "10.77.0.2/32",
    val serverPublicKey: String = "",
    val peerPublicKey: String = "",
    val egressInterface: String = "wlan0",
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val requiresVpnPermission: Boolean = false,
    val clientConfig: String = "",
    val error: String? = null,
)
