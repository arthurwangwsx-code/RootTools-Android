package com.arthur.roottools.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthStatusTest {
    @Test
    fun highSwapAloneDoesNotMeanMemoryPressure() {
        val memory = MemoryHealth(
            totalKb = 12_000_000,
            availableKb = 4_500_000,
            swapTotalKb = 4_000_000,
            swapFreeKb = 40_000,
            pressure = PressureMetric(someAvg10 = 0f, fullAvg10 = 0f),
        )
        assertEquals(MemoryPressureStatus.HEALTHY, memory.status)
    }

    @Test
    fun psiCanRaisePressureEvenWithAvailableMemory() {
        val memory = MemoryHealth(
            totalKb = 12_000_000,
            availableKb = 5_000_000,
            pressure = PressureMetric(someAvg10 = 12f),
        )
        assertEquals(MemoryPressureStatus.PRESSURE, memory.status)
    }

    @Test
    fun storageUsesAvailableRatio() {
        assertEquals(
            StorageStatus.LOW_SPACE,
            FileSystemUsage("Data", "dm", 100, 95, 5, 95, "/data").status,
        )
        assertEquals(
            StorageStatus.HEALTHY,
            FileSystemUsage("Data", "dm", 100, 50, 50, 50, "/data").status,
        )
    }
}
