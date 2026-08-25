package com.arthur.roottools.model

enum class PerformanceMode(val displayName: String) {
    AUTO("Auto"),
    COOL("Cool"),
    PERFORMANCE("Performance"),
}

enum class ThermalStage(val displayName: String) {
    NORMAL("Normal"),
    WARM("Warm"),
    MODERATE("Moderate"),
    SEVERE("Severe"),
}

data class CpuCluster(
    val policyId: Int,
    val relatedCpus: String,
    val hardwareMinKHz: Long,
    val hardwareMaxKHz: Long,
    val scalingMinKHz: Long,
    val scalingMaxKHz: Long,
    val currentKHz: Long,
    val availableKHz: List<Long>,
) {
    val label: String = when {
        policyId == 0 -> "能效核"
        else -> "性能簇 $policyId"
    }
}

data class DeviceSnapshot(
    val rootAvailable: Boolean = false,
    val model: String = "Android",
    val thermalStatus: Int = 0,
    val apTempC: Float? = null,
    val skinTempC: Float? = null,
    val batteryTempC: Float? = null,
    val batteryLevel: Int? = null,
    val charging: Boolean = false,
    val cpuClusters: List<CpuCluster> = emptyList(),
    val adbPort: Int? = null,
    val adbListening: Boolean = false,
    val tailscaleIpv4: String? = null,
    val runtimePressure: RuntimePressureSnapshot = RuntimePressureSnapshot(),
) {
    val adbEnabled: Boolean get() = adbPort != null && adbPort > 0 && adbListening

    fun thermalStage(): ThermalStage = when {
        thermalStatus >= 3 -> ThermalStage.SEVERE
        thermalStatus >= 2 -> ThermalStage.MODERATE
        thermalStatus >= 1 -> ThermalStage.WARM
        (skinTempC ?: 0f) >= 39f -> ThermalStage.MODERATE
        (skinTempC ?: 0f) >= 36.5f -> ThermalStage.WARM
        else -> ThermalStage.NORMAL
    }
}

data class AppliedCpuPolicy(
    val mode: PerformanceMode,
    val stage: ThermalStage,
    val maxByPolicyKHz: Map<Int, Long>,
)

