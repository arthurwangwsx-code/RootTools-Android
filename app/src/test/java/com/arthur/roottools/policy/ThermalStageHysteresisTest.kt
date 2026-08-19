package com.arthur.roottools.policy

import com.arthur.roottools.model.ThermalStage
import org.junit.Assert.assertEquals
import org.junit.Test

class ThermalStageHysteresisTest {
    @Test
    fun escalationIsImmediate() {
        val tracker = ThermalStageHysteresis(releaseDelayMs = 90_000)
        assertEquals(ThermalStage.WARM, tracker.update(ThermalStage.WARM, 0))
        assertEquals(ThermalStage.MODERATE, tracker.update(ThermalStage.MODERATE, 1_000))
        assertEquals(ThermalStage.SEVERE, tracker.update(ThermalStage.SEVERE, 2_000))
    }

    @Test
    fun coolingRequiresStableReleaseWindow() {
        val tracker = ThermalStageHysteresis(releaseDelayMs = 90_000)
        assertEquals(ThermalStage.MODERATE, tracker.update(ThermalStage.MODERATE, 0))
        assertEquals(ThermalStage.MODERATE, tracker.update(ThermalStage.WARM, 10_000))
        assertEquals(ThermalStage.MODERATE, tracker.update(ThermalStage.WARM, 99_999))
        assertEquals(ThermalStage.WARM, tracker.update(ThermalStage.WARM, 100_000))
    }

    @Test
    fun changingLowerCandidateRestartsTimer() {
        val tracker = ThermalStageHysteresis(releaseDelayMs = 90_000)
        tracker.update(ThermalStage.SEVERE, 0)
        assertEquals(ThermalStage.SEVERE, tracker.update(ThermalStage.MODERATE, 10_000))
        assertEquals(ThermalStage.SEVERE, tracker.update(ThermalStage.NORMAL, 60_000))
        assertEquals(ThermalStage.SEVERE, tracker.update(ThermalStage.NORMAL, 149_999))
        assertEquals(ThermalStage.NORMAL, tracker.update(ThermalStage.NORMAL, 150_000))
    }
}
