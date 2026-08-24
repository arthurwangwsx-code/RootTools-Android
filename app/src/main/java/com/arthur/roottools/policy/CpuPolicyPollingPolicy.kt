package com.arthur.roottools.policy

import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.model.ThermalStage

/** Pure cadence decision so long-running CPU policy monitoring remains cheap and testable. */
object CpuPolicyPollingPolicy {
    fun intervalMs(mode: PerformanceMode, stage: ThermalStage): Long = when {
        mode == PerformanceMode.COOL && stage <= ThermalStage.WARM -> COOL_STABLE_INTERVAL_MS
        else -> ACTIVE_INTERVAL_MS
    }

    private const val ACTIVE_INTERVAL_MS = 30_000L
    private const val COOL_STABLE_INTERVAL_MS = 60_000L
}
