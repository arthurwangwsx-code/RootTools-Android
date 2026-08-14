package com.arthur.nettools.capture

data class AppTarget(
    val label: String,
    val packageName: String,
    val uid: Int,
)

data class ProtocolCount(val protocol: String, val packets: Int)

data class FlowSummary(
    val protocol: String,
    val source: String,
    val destination: String,
    val host: String? = null,
    val hint: String? = null,
    val packets: Int = 1,
)

data class CaptureAnalysis(
    val packetCount: Int = 0,
    val byteCount: Long = 0,
    val protocols: List<ProtocolCount> = emptyList(),
    val flows: List<FlowSummary> = emptyList(),
)

data class CaptureSession(
    val id: String,
    val appLabel: String,
    val packageName: String,
    val uid: Int,
    val startedAt: Long,
    val stoppedAt: Long? = null,
    val pcapPath: String,
    val analysis: CaptureAnalysis? = null,
)

data class CaptureState(
    val rootAvailable: Boolean = false,
    val tcpdumpPath: String? = null,
    val active: CaptureSession? = null,
    val sessions: List<CaptureSession> = emptyList(),
    val message: String = "Ready",
)
