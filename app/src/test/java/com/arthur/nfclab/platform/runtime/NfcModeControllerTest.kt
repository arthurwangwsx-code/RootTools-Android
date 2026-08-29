package com.arthur.nfclab.platform.runtime

import com.arthur.nfclab.domain.NfcOperatingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcModeControllerTest {
    @Test
    fun readerMode_isOnlyEnabledWhileResumed() {
        val driver = FakeDriver()
        val controller = NfcModeController(driver)

        controller.setMode(NfcOperatingMode.READER)
        assertEquals(0, driver.enableCount)
        assertFalse(driver.hceEnabled)

        controller.onResume()
        assertEquals(1, driver.enableCount)

        controller.onPause()
        assertTrue(driver.disableCount >= 1)
        assertFalse(controller.isResumed)
    }

    @Test
    fun defaultAndHce_applyDistinctHceStateAndLeaveReaderDisabled() {
        val driver = FakeDriver()
        val controller = NfcModeController(driver)
        controller.onResume()

        controller.setMode(NfcOperatingMode.DEFAULT)
        assertFalse(driver.hceEnabled)

        controller.setMode(NfcOperatingMode.HCE)
        assertTrue(driver.hceEnabled)

        assertEquals(0, driver.enableCount)
        assertTrue(driver.disableCount >= 2)
    }

    @Test
    fun switchingFromHceToReader_disablesTestServiceBeforeReaderMode() {
        val driver = FakeDriver()
        val controller = NfcModeController(driver)
        controller.onResume()
        controller.setMode(NfcOperatingMode.HCE)
        assertTrue(driver.hceEnabled)

        controller.setMode(NfcOperatingMode.READER)

        assertFalse(driver.hceEnabled)
        assertEquals(1, driver.enableCount)
    }

    @Test
    fun rearm_requiresForegroundReaderMode() {
        val driver = FakeDriver()
        val controller = NfcModeController(driver)
        assertFalse(controller.rearmReaderMode())

        controller.setMode(NfcOperatingMode.READER)
        controller.onResume()
        val before = driver.enableCount

        assertTrue(controller.rearmReaderMode())
        assertEquals(before + 1, driver.enableCount)
    }

    private class FakeDriver : NfcModeDriver {
        override val available: Boolean = true
        override val enabled: Boolean = true
        var enableCount = 0
        var disableCount = 0
        var hceEnabled = false

        override fun setHceTestServiceEnabled(enabled: Boolean) {
            hceEnabled = enabled
        }

        override fun enableReaderMode() {
            enableCount++
        }

        override fun disableReaderMode() {
            disableCount++
        }
    }
}
