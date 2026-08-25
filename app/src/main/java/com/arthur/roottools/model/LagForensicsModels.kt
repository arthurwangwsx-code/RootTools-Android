package com.arthur.roottools.model

enum class LagPressureLevel {
    NORMAL,
    ELEVATED,
    SEVERE,
}

data class RuntimePressureSnapshot(
    val memory: PressureMetric = PressureMetric(),
    val io: PressureMetric = PressureMetric(),
    val cpu: PressureMetric = PressureMetric(),
    val memTotalKb: Long = 0,
    val memAvailableKb: Long = 0,
    val swapTotalKb: Long = 0,
    val swapFreeKb: Long = 0,
) {
    val memAvailableRatio: Float
        get() = if (memTotalKb > 0) memAvailableKb.toFloat() / memTotalKb else 1f

    val swapUsedKb: Long
        get() = (swapTotalKb - swapFreeKb).coerceAtLeast(0)
}

data class LagForensicsSample(
    val timestampMs: Long,
    val level: LagPressureLevel,
    val memorySome10: Float,
    val memoryFull10: Float,
    val ioSome10: Float,
    val ioFull10: Float,
    val cpuSome10: Float,
    val memAvailableRatio: Float,
    val swapUsedKb: Long,
    val thermalStatus: Int,
    val skinTempC: Float?,
)

data class LagIncidentSummary(
    val capturedAtMs: Long,
    val level: LagPressureLevel,
    val reason: String,
    val memorySome10: Float,
    val memoryFull10: Float,
    val ioSome10: Float,
    val ioFull10: Float,
    val cpuSome10: Float,
    val memAvailableRatio: Float,
    val evidenceFileName: String,
)

data class LagForensicsState(
    val enabled: Boolean = true,
    val latestSample: LagForensicsSample? = null,
    val consecutivePressureSamples: Int = 0,
    val captureInProgress: Boolean = false,
    val incidents: List<LagIncidentSummary> = emptyList(),
)
