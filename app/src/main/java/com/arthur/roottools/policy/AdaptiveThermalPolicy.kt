package com.arthur.roottools.policy

import com.arthur.roottools.model.DeviceSnapshot
import com.arthur.roottools.model.AdaptiveThermalReason
import com.arthur.roottools.model.ThermalStage

/**
 * User-comfort thermal policy for an always-capable AI handset.
 *
 * Android/OEM thermal remains authoritative. This policy intervenes earlier than emergency thermal
 * throttling to avoid sustained warm-to-the-touch operation while retaining burst responsiveness.
 */
object AdaptiveThermalPolicy {
    data class Decision(
        val stage: ThermalStage,
        val reason: AdaptiveThermalReason,
    )

    fun decide(snapshot: DeviceSnapshot, interactive: Boolean): Decision {
        var decision = Decision(ThermalStage.NORMAL, AdaptiveThermalReason.NORMAL)

        fun raise(stage: ThermalStage, reason: AdaptiveThermalReason) {
            if (stage > decision.stage) decision = Decision(stage, reason)
        }

        when {
            snapshot.thermalStatus >= 3 -> raise(ThermalStage.SEVERE, AdaptiveThermalReason.SYSTEM_THERMAL)
            snapshot.thermalStatus >= 2 -> raise(ThermalStage.MODERATE, AdaptiveThermalReason.SYSTEM_THERMAL)
            snapshot.thermalStatus >= 1 -> raise(ThermalStage.WARM, AdaptiveThermalReason.SYSTEM_THERMAL)
        }

        snapshot.skinTempC?.let { skin ->
            when {
                skin >= SKIN_SEVERE_C -> raise(ThermalStage.SEVERE, AdaptiveThermalReason.SKIN_TEMPERATURE)
                skin >= SKIN_MODERATE_C -> raise(ThermalStage.MODERATE, AdaptiveThermalReason.SKIN_TEMPERATURE)
                skin >= SKIN_WARM_C -> raise(ThermalStage.WARM, AdaptiveThermalReason.SKIN_TEMPERATURE)
            }
        }

        snapshot.batteryTempC?.let { battery ->
            when {
                battery >= BATTERY_SEVERE_C -> raise(ThermalStage.SEVERE, AdaptiveThermalReason.BATTERY_TEMPERATURE)
                battery >= BATTERY_MODERATE_C -> raise(ThermalStage.MODERATE, AdaptiveThermalReason.BATTERY_TEMPERATURE)
                battery >= BATTERY_WARM_C -> raise(ThermalStage.WARM, AdaptiveThermalReason.BATTERY_TEMPERATURE)
            }
        }

        if (snapshot.charging && maxOf(snapshot.skinTempC ?: 0f, snapshot.batteryTempC ?: 0f) >= CHARGING_WARM_C) {
            raise(ThermalStage.WARM, AdaptiveThermalReason.CHARGING_HEAT)
        }

        // When nobody is touching the phone, preserve capability but trim the inefficient peak tail.
        // The existing WARM frequency target keeps the efficiency cluster at full peak.
        if (!interactive) raise(ThermalStage.WARM, AdaptiveThermalReason.BACKGROUND_EFFICIENCY)

        return decision
    }

    private const val SKIN_WARM_C = 38.0f
    private const val SKIN_MODERATE_C = 40.0f
    private const val SKIN_SEVERE_C = 42.0f
    private const val BATTERY_WARM_C = 37.5f
    private const val BATTERY_MODERATE_C = 39.5f
    private const val BATTERY_SEVERE_C = 42.0f
    private const val CHARGING_WARM_C = 37.0f
}
