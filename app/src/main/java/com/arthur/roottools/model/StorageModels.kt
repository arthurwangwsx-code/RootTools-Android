package com.arthur.roottools.model

enum class StorageStatus(val displayName: String) {
    HEALTHY("Healthy"),
    WATCH("Watch"),
    LOW_SPACE("Low space"),
}

data class FileSystemUsage(
    val label: String,
    val filesystem: String,
    val totalKb: Long,
    val usedKb: Long,
    val availableKb: Long,
    val usedPercent: Int,
    val mountedOn: String,
) {
    val availableRatio: Float get() = if (totalKb > 0) availableKb.toFloat() / totalKb else 0f
    val status: StorageStatus
        get() = when {
            availableRatio < 0.10f -> StorageStatus.LOW_SPACE
            availableRatio < 0.20f -> StorageStatus.WATCH
            else -> StorageStatus.HEALTHY
        }
}

data class BlockDeviceStat(
    val name: String,
    val readsCompleted: Long,
    val sectorsRead: Long,
    val writesCompleted: Long,
    val sectorsWritten: Long,
    val ioTimeMs: Long,
) {
    val readGb: Double get() = sectorsRead * 512.0 / 1_073_741_824.0
    val writtenGb: Double get() = sectorsWritten * 512.0 / 1_073_741_824.0
}

data class StorageSnapshot(
    val fileSystems: List<FileSystemUsage> = emptyList(),
    val ioPressure: PressureMetric = PressureMetric(),
    val blockDevices: List<BlockDeviceStat> = emptyList(),
) {
    val primary: FileSystemUsage? get() = fileSystems.firstOrNull { it.label == "Data" } ?: fileSystems.firstOrNull()
    val ioStatus: MemoryPressureStatus
        get() = when {
            ioPressure.someAvg10 >= 10f || ioPressure.fullAvg10 >= 2f -> MemoryPressureStatus.PRESSURE
            ioPressure.someAvg10 >= 2f -> MemoryPressureStatus.WATCH
            else -> MemoryPressureStatus.HEALTHY
        }
}
