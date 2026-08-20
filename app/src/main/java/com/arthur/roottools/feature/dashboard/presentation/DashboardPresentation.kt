package com.arthur.roottools.feature.dashboard.presentation

import com.arthur.roottools.model.AppPolicyCategory
import com.arthur.roottools.model.DeviceHealthSnapshot
import com.arthur.roottools.model.HealthHistoryPoint
import kotlin.math.roundToInt

internal fun categoryOrder(category: AppPolicyCategory): Int = when (category) {
    AppPolicyCategory.FREEZE -> 0
    AppPolicyCategory.ON_DEMAND -> 1
    AppPolicyCategory.RARE -> 2
    AppPolicyCategory.PROTECTED -> 3
    AppPolicyCategory.NORMAL -> 4
}

internal fun DeviceHealthSnapshot.thermalStageLabel(): String = when {
    thermal.status >= 3 -> "Severe"
    thermal.status >= 2 -> "Moderate"
    thermal.status >= 1 -> "Warm"
    (thermal.skinC ?: 0f) >= 39f -> "Moderate"
    (thermal.skinC ?: 0f) >= 36.5f -> "Warm"
    else -> "Normal"
}

internal fun rangeText(values: List<Float>): String = if (values.isEmpty()) {
    "—"
} else {
    "%.1f°C / %.1f°C".format(values.minOrNull() ?: 0f, values.maxOrNull() ?: 0f)
}

internal data class FrequencyBins(
    val low: Int = 0,
    val mid: Int = 0,
    val high: Int = 0,
    val peak: Int = 0,
    val total: Int = 0,
) {
    fun asLabel(): String {
        if (total <= 0) return "—"
        fun pct(value: Int) = (value * 100f / total).roundToInt()
        return "${pct(low)}% · ${pct(mid)}% · ${pct(high)}% · ${pct(peak)}%"
    }
}

internal fun frequencyBins(
    history: List<HealthHistoryPoint>,
    policyId: Int,
    hardwareMaxKHz: Long,
    windowMs: Long,
): FrequencyBins {
    if (hardwareMaxKHz <= 0) return FrequencyBins()
    val cutoff = System.currentTimeMillis() - windowMs
    var low = 0
    var mid = 0
    var high = 0
    var peak = 0
    var total = 0
    history.asSequence().filter { it.timestampMs >= cutoff }.forEach { point ->
        val current = point.clusterCurrentKHz[policyId] ?: return@forEach
        val ratio = current.toDouble() / hardwareMaxKHz
        when {
            ratio < 0.35 -> low++
            ratio < 0.60 -> mid++
            ratio < 0.80 -> high++
            else -> peak++
        }
        total++
    }
    return FrequencyBins(low, mid, high, peak, total)
}

