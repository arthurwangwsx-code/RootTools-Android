package com.arthur.nfclab.platform.runtime

import com.arthur.nfclab.domain.NfcOperatingMode

class NfcModeController(
    private val driver: NfcModeDriver,
    private val onApplied: (mode: NfcOperatingMode, readerEnabled: Boolean) -> Unit = { _, _ -> },
) {
    var desiredMode: NfcOperatingMode = NfcOperatingMode.DEFAULT
        private set

    var isResumed: Boolean = false
        private set

    fun setMode(mode: NfcOperatingMode) {
        desiredMode = mode
        applyDesiredMode()
    }

    fun onResume() {
        isResumed = true
        applyDesiredMode()
    }

    fun onPause() {
        isResumed = false
        driver.disableReaderMode()
        onApplied(desiredMode, false)
    }

    fun applyDesiredMode() {
        driver.setHceTestServiceEnabled(desiredMode == NfcOperatingMode.HCE)

        val shouldEnableReader = isResumed &&
            desiredMode == NfcOperatingMode.READER &&
            driver.enabled

        if (shouldEnableReader) {
            driver.enableReaderMode()
        } else {
            driver.disableReaderMode()
        }
        onApplied(desiredMode, shouldEnableReader)
    }

    fun rearmReaderMode(): Boolean {
        if (!isResumed || desiredMode != NfcOperatingMode.READER || !driver.enabled) return false
        driver.disableReaderMode()
        driver.enableReaderMode()
        onApplied(desiredMode, true)
        return true
    }
}
