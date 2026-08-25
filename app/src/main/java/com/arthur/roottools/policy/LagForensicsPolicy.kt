package com.arthur.roottools.policy

import com.arthur.roottools.model.LagPressureLevel
import com.arthur.roottools.model.RuntimePressureSnapshot

object LagForensicsPolicy {
    fun level(pressure: RuntimePressureSnapshot): LagPressureLevel = when {
        pressure.memory.fullAvg10 >= 10f ||
            pressure.memory.someAvg10 >= 25f ||
            pressure.io.fullAvg10 >= 10f ||
            pressure.io.someAvg10 >= 30f ||
            pressure.cpu.someAvg10 >= 60f ||
            pressure.memAvailableRatio < 0.06f -> LagPressureLevel.SEVERE

        pressure.memory.fullAvg10 >= 2f ||
            pressure.memory.someAvg10 >= 10f ||
            pressure.io.fullAvg10 >= 3f ||
            pressure.io.someAvg10 >= 10f ||
            pressure.cpu.someAvg10 >= 40f ||
            pressure.memAvailableRatio < 0.10f -> LagPressureLevel.ELEVATED

        else -> LagPressureLevel.NORMAL
    }

    fun shouldCapture(
        level: LagPressureLevel,
        consecutivePressureSamples: Int,
        nowMs: Long,
        lastCaptureAtMs: Long,
    ): Boolean {
        if (nowMs - lastCaptureAtMs < CAPTURE_COOLDOWN_MS) return false
        return level == LagPressureLevel.SEVERE ||
            (level == LagPressureLevel.ELEVATED && consecutivePressureSamples >= REQUIRED_ELEVATED_SAMPLES)
    }

    const val REQUIRED_ELEVATED_SAMPLES = 2
    const val CAPTURE_COOLDOWN_MS = 10 * 60_000L
}
