package com.arthur.roottools.policy

import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.model.ThermalStage
import org.junit.Assert.assertEquals
import org.junit.Test

class CpuPolicyPollingPolicyTest {
    @Test
    fun coolNormalOrWarm_usesLowFrequencyReconciliation() {
        assertEquals(
            60_000L,
            CpuPolicyPollingPolicy.intervalMs(PerformanceMode.COOL, ThermalStage.NORMAL),
        )
        assertEquals(
            60_000L,
            CpuPolicyPollingPolicy.intervalMs(PerformanceMode.COOL, ThermalStage.WARM),
        )
    }

    @Test
    fun coolHotStage_reactsAtNormalThermalCadence() {
        assertEquals(
            30_000L,
            CpuPolicyPollingPolicy.intervalMs(PerformanceMode.COOL, ThermalStage.MODERATE),
        )
        assertEquals(
            30_000L,
            CpuPolicyPollingPolicy.intervalMs(PerformanceMode.COOL, ThermalStage.SEVERE),
        )
    }

    @Test
    fun autoAndPerformance_keepNormalCadence() {
        ThermalStage.entries.forEach { stage ->
            assertEquals(30_000L, CpuPolicyPollingPolicy.intervalMs(PerformanceMode.AUTO, stage))
            assertEquals(30_000L, CpuPolicyPollingPolicy.intervalMs(PerformanceMode.PERFORMANCE, stage))
        }
    }
}
