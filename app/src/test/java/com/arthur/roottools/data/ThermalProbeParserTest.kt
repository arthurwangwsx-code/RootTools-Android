package com.arthur.roottools.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ThermalProbeParserTest {
    @Test
    fun xiaomiLowercaseSensors_prefersCurrentHalAndUsesCpuFallbackForAp() {
        val snapshot = ThermalProbeParser.parse(
            listOf(
                "Thermal Status: 0",
                "Temperature{mValue=41.475, mType=3, mName=skin, mStatus=0}",
                "Current temperatures from HAL:",
                "Temperature{mValue=32.8, mType=2, mName=battery, mStatus=0}",
                "Temperature{mValue=37.484, mType=3, mName=skin, mStatus=0}",
                "Temperature{mValue=49.2, mType=0, mName=CPU5, mStatus=0}",
                "Temperature{mValue=52.3, mType=0, mName=CPU1, mStatus=0}",
                "Current cooling devices from HAL:",
            ),
        )

        assertEquals(0, snapshot.status)
        assertEquals(52.3f, snapshot.apC)
        assertEquals(37.484f, snapshot.skinC)
        assertEquals(32.8f, snapshot.batteryC)
    }

    @Test
    fun samsungNamedSensors_areParsedWithoutCurrentSection() {
        val snapshot = ThermalProbeParser.parse(
            listOf(
                "Thermal Status: 1",
                "Temperature{mValue=43.0, mType=0, mName=AP, mStatus=1}",
                "Temperature{mValue=36.0, mType=2, mName=BAT, mStatus=0}",
                "Temperature{mValue=38.5, mType=3, mName=SKIN, mStatus=1}",
                "Temperature{mValue=35.0, mType=3, mName=USB, mStatus=0}",
            ),
        )

        assertEquals(1, snapshot.status)
        assertEquals(43.0f, snapshot.apC)
        assertEquals(36.0f, snapshot.batteryC)
        assertEquals(38.5f, snapshot.skinC)
        assertEquals(35.0f, snapshot.usbC)
    }
}
