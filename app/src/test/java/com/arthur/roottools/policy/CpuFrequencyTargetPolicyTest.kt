package com.arthur.roottools.policy

import com.arthur.roottools.model.CpuCluster
import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.model.ThermalStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuFrequencyTargetPolicyTest {
    @Test
    fun coolResponsive_keepsEfficiencyClusterAtFullPeak() {
        val cluster = cluster(
            policyId = 0,
            hardwareMaxKHz = 2_265_600,
            frequencies = listOf(1_804_800, 2_035_200, 2_265_600),
        )

        val target = CpuFrequencyTargetPolicy.target(
            cluster = cluster,
            index = 0,
            lastIndex = 3,
            mode = PerformanceMode.COOL,
            stage = ThermalStage.WARM,
        )

        assertEquals(2_265_600L, target)
    }

    @Test
    fun coolResponsive_trimsPerformanceAndPrimeHighFrequencyTail() {
        val performance = cluster(
            policyId = 2,
            hardwareMaxKHz = 3_148_800,
            frequencies = listOf(2_515_200, 2_630_400, 2_707_200, 2_764_800, 3_148_800),
        )
        val prime = cluster(
            policyId = 7,
            hardwareMaxKHz = 3_302_400,
            frequencies = listOf(2_438_400, 2_553_600, 2_688_000, 2_745_600, 3_302_400),
        )

        val performanceTarget = CpuFrequencyTargetPolicy.target(
            performance,
            index = 1,
            lastIndex = 3,
            mode = PerformanceMode.COOL,
            stage = ThermalStage.WARM,
        )
        val primeTarget = CpuFrequencyTargetPolicy.target(
            prime,
            index = 3,
            lastIndex = 3,
            mode = PerformanceMode.COOL,
            stage = ThermalStage.WARM,
        )

        assertEquals(2_707_200L, performanceTarget)
        assertEquals(2_553_600L, primeTarget)
        assertTrue(performanceTarget < performance.hardwareMaxKHz)
        assertTrue(primeTarget < prime.hardwareMaxKHz)
    }

    @Test
    fun hotterThermalStageAlwaysWinsOverCoolResponsiveFloor() {
        val prime = cluster(
            policyId = 7,
            hardwareMaxKHz = 3_302_400,
            frequencies = listOf(2_112_000, 2_438_400, 2_496_000, 2_553_600, 3_302_400),
        )

        val coolWarm = CpuFrequencyTargetPolicy.target(
            prime,
            index = 3,
            lastIndex = 3,
            mode = PerformanceMode.COOL,
            stage = ThermalStage.WARM,
        )
        val coolModerate = CpuFrequencyTargetPolicy.target(
            prime,
            index = 3,
            lastIndex = 3,
            mode = PerformanceMode.COOL,
            stage = ThermalStage.MODERATE,
        )

        assertEquals(2_553_600L, coolWarm)
        assertEquals(2_496_000L, coolModerate)
        assertTrue(coolModerate < coolWarm)
    }

    @Test
    fun performanceNormal_keepsHardwarePeakAvailable() {
        val prime = cluster(
            policyId = 7,
            hardwareMaxKHz = 3_302_400,
            frequencies = listOf(2_553_600, 2_745_600, 3_302_400),
        )

        val target = CpuFrequencyTargetPolicy.target(
            prime,
            index = 3,
            lastIndex = 3,
            mode = PerformanceMode.PERFORMANCE,
            stage = ThermalStage.NORMAL,
        )

        assertEquals(3_302_400L, target)
    }

    private fun cluster(
        policyId: Int,
        hardwareMaxKHz: Long,
        frequencies: List<Long>,
    ) = CpuCluster(
        policyId = policyId,
        relatedCpus = policyId.toString(),
        hardwareMinKHz = frequencies.first(),
        hardwareMaxKHz = hardwareMaxKHz,
        scalingMinKHz = frequencies.first(),
        scalingMaxKHz = hardwareMaxKHz,
        currentKHz = frequencies.first(),
        availableKHz = frequencies,
    )
}
