package com.arthur.roottools.policy

import com.arthur.roottools.model.DeviceSnapshot
import com.arthur.roottools.model.AdaptiveThermalReason
import com.arthur.roottools.model.ThermalStage
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveThermalPolicyTest {
    @Test
    fun interactiveCoolDevice_keepsFullResponsiveStage() {
        val decision = AdaptiveThermalPolicy.decide(
            DeviceSnapshot(skinTempC = 35.5f, batteryTempC = 31.0f),
            interactive = true,
        )

        assertEquals(ThermalStage.NORMAL, decision.stage)
        assertEquals(AdaptiveThermalReason.NORMAL, decision.reason)
    }

    @Test
    fun backgroundDevice_trimsPeakTailWithoutDisablingCapability() {
        val decision = AdaptiveThermalPolicy.decide(
            DeviceSnapshot(skinTempC = 35.5f, batteryTempC = 31.0f),
            interactive = false,
        )

        assertEquals(ThermalStage.WARM, decision.stage)
        assertEquals(AdaptiveThermalReason.BACKGROUND_EFFICIENCY, decision.reason)
    }

    @Test
    fun chargingHeat_preemptivelyEntersWarmStage() {
        val decision = AdaptiveThermalPolicy.decide(
            DeviceSnapshot(charging = true, skinTempC = 37.2f, batteryTempC = 35.0f),
            interactive = true,
        )

        assertEquals(ThermalStage.WARM, decision.stage)
        assertEquals(AdaptiveThermalReason.CHARGING_HEAT, decision.reason)
    }

    @Test
    fun skinTemperature_escalatesBeforeOemEmergencyThermal() {
        assertEquals(
            ThermalStage.MODERATE,
            AdaptiveThermalPolicy.decide(DeviceSnapshot(skinTempC = 40.2f), interactive = true).stage,
        )
        assertEquals(
            ThermalStage.SEVERE,
            AdaptiveThermalPolicy.decide(DeviceSnapshot(skinTempC = 42.1f), interactive = true).stage,
        )
    }

    @Test
    fun systemThermalSignal_hasPriorityOverCoolTemperatures() {
        val decision = AdaptiveThermalPolicy.decide(
            DeviceSnapshot(thermalStatus = 3, skinTempC = 34.0f, batteryTempC = 31.0f),
            interactive = true,
        )

        assertEquals(ThermalStage.SEVERE, decision.stage)
        assertEquals(AdaptiveThermalReason.SYSTEM_THERMAL, decision.reason)
    }
}
