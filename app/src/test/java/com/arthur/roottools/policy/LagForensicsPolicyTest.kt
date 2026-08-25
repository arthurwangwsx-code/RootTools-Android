package com.arthur.roottools.policy

import com.arthur.roottools.model.LagPressureLevel
import com.arthur.roottools.model.PressureMetric
import com.arthur.roottools.model.RuntimePressureSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LagForensicsPolicyTest {
    @Test
    fun healthyPressure_staysNormal() {
        assertEquals(
            LagPressureLevel.NORMAL,
            LagForensicsPolicy.level(
                RuntimePressureSnapshot(
                    memory = PressureMetric(someAvg10 = 1f, fullAvg10 = 0.1f),
                    io = PressureMetric(someAvg10 = 1f, fullAvg10 = 0.1f),
                    cpu = PressureMetric(someAvg10 = 8f),
                    memTotalKb = 16_000,
                    memAvailableKb = 8_000,
                ),
            ),
        )
    }

    @Test
    fun sustainedMemoryOrIoPressure_isElevated() {
        assertEquals(
            LagPressureLevel.ELEVATED,
            LagForensicsPolicy.level(
                RuntimePressureSnapshot(
                    memory = PressureMetric(someAvg10 = 12f, fullAvg10 = 3f),
                    io = PressureMetric(someAvg10 = 14f, fullAvg10 = 4f),
                    memTotalKb = 16_000,
                    memAvailableKb = 4_000,
                ),
            ),
        )
    }

    @Test
    fun incidentLikePressure_isSevere() {
        assertEquals(
            LagPressureLevel.SEVERE,
            LagForensicsPolicy.level(
                RuntimePressureSnapshot(
                    memory = PressureMetric(someAvg10 = 37f, fullAvg10 = 18f),
                    io = PressureMetric(someAvg10 = 52f, fullAvg10 = 20f),
                    cpu = PressureMetric(someAvg10 = 59f),
                    memTotalKb = 16_000,
                    memAvailableKb = 800,
                ),
            ),
        )
    }

    @Test
    fun elevatedNeedsTwoSamplesAndCooldown() {
        val now = 1_000_000L
        assertFalse(LagForensicsPolicy.shouldCapture(LagPressureLevel.ELEVATED, 1, now, 0L))
        assertTrue(LagForensicsPolicy.shouldCapture(LagPressureLevel.ELEVATED, 2, now, 0L))
        assertFalse(LagForensicsPolicy.shouldCapture(LagPressureLevel.SEVERE, 1, now, now - 60_000L))
    }
}
