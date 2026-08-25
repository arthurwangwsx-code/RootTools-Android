package com.arthur.roottools.policy

import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.model.ThermalStage

/** Pure cadence decision so long-running CPU policy monitoring remains cheap and testable. */
object CpuPolicyPollingPolicy {
    fun intervalMs(mode: PerformanceMode, stage: ThermalStage, interactive: Boolean = true): Long = when {
        stage >= ThermalStage.WARM -> ACTIVE_INTERVAL_MS
        mode == PerformanceMode.PERFORMANCE && interactive -> ACTIVE_INTERVAL_MS
        mode == PerformanceMode.PERFORMANCE -> PERFORMANCE_SCREEN_OFF_INTERVAL_MS
        interactive -> INTERACTIVE_STABLE_INTERVAL_MS
        else -> SCREEN_OFF_STABLE_INTERVAL_MS
    }

    private const val ACTIVE_INTERVAL_MS = 30_000L
    private const val PERFORMANCE_SCREEN_OFF_INTERVAL_MS = 60_000L
    private const val INTERACTIVE_STABLE_INTERVAL_MS = 60_000L
    private const val SCREEN_OFF_STABLE_INTERVAL_MS = 120_000L
}
