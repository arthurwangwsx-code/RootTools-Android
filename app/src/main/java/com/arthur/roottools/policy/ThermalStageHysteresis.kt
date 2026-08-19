package com.arthur.roottools.policy

import com.arthur.roottools.model.ThermalStage

/**
 * Escalates immediately when the device gets hotter, but requires a stable cool-down window
 * before releasing a cap. This prevents Normal/Warm/Moderate oscillation around thresholds.
 */
class ThermalStageHysteresis(
    private val releaseDelayMs: Long = 90_000L,
) {
    private var currentStage: ThermalStage = ThermalStage.NORMAL
    private var lowerCandidate: ThermalStage? = null
    private var lowerSinceMs: Long = 0L

    fun update(requested: ThermalStage, nowMs: Long = System.currentTimeMillis()): ThermalStage {
        when {
            requested > currentStage -> {
                currentStage = requested
                clearLowerCandidate()
            }
            requested == currentStage -> clearLowerCandidate()
            else -> {
                if (lowerCandidate != requested) {
                    lowerCandidate = requested
                    lowerSinceMs = nowMs
                } else if (nowMs - lowerSinceMs >= releaseDelayMs) {
                    currentStage = requested
                    clearLowerCandidate()
                }
            }
        }
        return currentStage
    }

    fun reset(stage: ThermalStage = ThermalStage.NORMAL) {
        currentStage = stage
        clearLowerCandidate()
    }

    private fun clearLowerCandidate() {
        lowerCandidate = null
        lowerSinceMs = 0L
    }
}
