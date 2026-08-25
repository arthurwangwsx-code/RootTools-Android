package com.arthur.roottools.policy

import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.model.ThermalStage
import org.junit.Assert.assertEquals
import org.junit.Test

class CpuPolicyPollingPolicyTest {
    @Test
    fun stableInteractive_usesOneMinuteCadence() {
        assertEquals(
            60_000L,
            CpuPolicyPollingPolicy.intervalMs(PerformanceMode.COOL, ThermalStage.NORMAL),
        )
        assertEquals(
            60_000L,
            CpuPolicyPollingPolicy.intervalMs(PerformanceMode.AUTO, ThermalStage.NORMAL),
        )
    }

    @Test
    fun stableScreenOff_usesTwoMinuteCadence() {
        assertEquals(
            120_000L,
            CpuPolicyPollingPolicy.intervalMs(PerformanceMode.AUTO, ThermalStage.NORMAL, interactive = false),
        )
        assertEquals(
            120_000L,
            CpuPolicyPollingPolicy.intervalMs(PerformanceMode.COOL, ThermalStage.NORMAL, interactive = false),
        )
    }

    @Test
    fun warmOrHotStage_reactsAtThirtySecondCadence() {
        assertEquals(
            30_000L,
            CpuPolicyPollingPolicy.intervalMs(PerformanceMode.COOL, ThermalStage.WARM),
        )
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
    fun interactivePerformance_keepsThirtySecondCadence() {
        assertEquals(
            30_000L,
            CpuPolicyPollingPolicy.intervalMs(PerformanceMode.PERFORMANCE, ThermalStage.NORMAL, interactive = true),
        )
        assertEquals(
            60_000L,
            CpuPolicyPollingPolicy.intervalMs(PerformanceMode.PERFORMANCE, ThermalStage.NORMAL, interactive = false),
        )
    }
}
