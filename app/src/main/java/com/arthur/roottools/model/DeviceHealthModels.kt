package com.arthur.roottools.model

data class CpuHealthCluster(
    val policyId: Int,
    val relatedCpus: String,
    val utilizationPercent: Float = 0f,
    val currentKHz: Long,
    val scalingMinKHz: Long,
    val scalingMaxKHz: Long,
    val hardwareMaxKHz: Long,
    val governor: String,
)

data class SchedulerGroupHealth(
    val name: String,
    val cpus: String = "",
    val uclampMin: String? = null,
    val uclampMax: String? = null,
)

data class SchedulerHealth(
    val groups: List<SchedulerGroupHealth> = emptyList(),
)

data class PressureMetric(
    val someAvg10: Float = 0f,
    val someAvg60: Float = 0f,
    val someAvg300: Float = 0f,
    val fullAvg10: Float = 0f,
    val fullAvg60: Float = 0f,
    val fullAvg300: Float = 0f,
)

data class MemoryHealth(
    val totalKb: Long = 0,
    val availableKb: Long = 0,
    val cachedKb: Long = 0,
    val anonKb: Long = 0,
    val slabKb: Long = 0,
    val swapTotalKb: Long = 0,
    val swapFreeKb: Long = 0,
    val zramOriginalBytes: Long = 0,
    val zramCompressedBytes: Long = 0,
    val zramMemoryBytes: Long = 0,
    val pressure: PressureMetric = PressureMetric(),
) {
    val swapUsedKb: Long get() = (swapTotalKb - swapFreeKb).coerceAtLeast(0)
    val availableRatio: Float get() = if (totalKb > 0) availableKb.toFloat() / totalKb else 0f
    val swapUsedRatio: Float get() = if (swapTotalKb > 0) swapUsedKb.toFloat() / swapTotalKb else 0f
    val compressionRatio: Float
        get() = if (zramCompressedBytes > 0) zramOriginalBytes.toFloat() / zramCompressedBytes else 0f

    val status: MemoryPressureStatus
        get() = when {
            pressure.someAvg10 >= 10f || pressure.fullAvg10 >= 2f || availableRatio < 0.10f -> MemoryPressureStatus.PRESSURE
            pressure.someAvg10 >= 2f || availableRatio < 0.20f -> MemoryPressureStatus.WATCH
            else -> MemoryPressureStatus.HEALTHY
        }
}

enum class MemoryPressureStatus(val displayName: String) {
    HEALTHY("Healthy"),
    WATCH("Watch"),
    PRESSURE("Pressure"),
}

data class ThermalHealth(
    val status: Int = 0,
    val apC: Float? = null,
    val skinC: Float? = null,
    val batteryC: Float? = null,
    val usbC: Float? = null,
    val pathmC: Float? = null,
)

data class BatteryHealth(
    val level: Int? = null,
    val charging: Boolean = false,
    val voltageMv: Int? = null,
    val currentMa: Int? = null,
    val protectionEnabled: Boolean = false,
    val protectionThreshold: Int? = null,
)

data class ProcessHealth(
    val pid: Int,
    val user: String,
    val cpuPercent: Float,
    val memoryPercent: Float,
    val rss: String,
    val processName: String,
) {
    val isRootShell: Boolean get() = user == "root" && (processName == "sh" || processName.endsWith("/sh"))
}

data class MemoryProcessHealth(
    val pid: Int,
    val user: String,
    val rssKb: Long,
    val pssKb: Long,
    val processName: String,
)

data class LmkHealth(
    val recentKillCount: Int = 0,
    val chimeraKillCount: Int = 0,
    val recentReasons: List<String> = emptyList(),
    val config: Map<String, String> = emptyMap(),
)

data class DeviceHealthSnapshot(
    val timestampMs: Long = System.currentTimeMillis(),
    val rootAvailable: Boolean = false,
    val cpuUsagePercent: Float = 0f,
    val cpuIdlePercent: Float = 100f,
    val load1: Float = 0f,
    val load5: Float = 0f,
    val load15: Float = 0f,
    val cpuClusters: List<CpuHealthCluster> = emptyList(),
    val scheduler: SchedulerHealth = SchedulerHealth(),
    val memory: MemoryHealth = MemoryHealth(),
    val ioPressure: PressureMetric = PressureMetric(),
    val thermal: ThermalHealth = ThermalHealth(),
    val battery: BatteryHealth = BatteryHealth(),
    val uptimeSeconds: Long = 0,
    val processCount: Int = 0,
    val topProcesses: List<ProcessHealth> = emptyList(),
    val topMemoryProcesses: List<MemoryProcessHealth> = emptyList(),
    val lmk: LmkHealth = LmkHealth(),
) {
    val hottestProcess: ProcessHealth? get() = topProcesses.maxByOrNull { it.cpuPercent }
    val abnormalRootShell: ProcessHealth? get() = topProcesses.firstOrNull { it.isRootShell && it.cpuPercent >= 50f }
}

data class HealthHistoryPoint(
    val timestampMs: Long,
    val cpuUsagePercent: Float,
    val memoryAvailableKb: Long,
    val apTempC: Float?,
    val skinTempC: Float?,
    val batteryLevel: Int? = null,
    val thermalStatus: Int,
    val clusterCurrentKHz: Map<Int, Long> = emptyMap(),
)
