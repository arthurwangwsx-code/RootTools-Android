package com.arthur.nfclab.platform.runtime

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Bundle
import com.arthur.nfclab.hce.LabHostApduService

class AndroidNfcModeDriver(
    private val activity: Activity,
    private val adapter: NfcAdapter?,
    private val callback: NfcAdapter.ReaderCallback,
) : NfcModeDriver {
    private val hceServiceComponent = ComponentName(activity, LabHostApduService::class.java)

    override val available: Boolean
        get() = adapter != null

    override val enabled: Boolean
        get() = adapter?.isEnabled == true

    override fun setHceTestServiceEnabled(enabled: Boolean) {
        val targetState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (activity.packageManager.getComponentEnabledSetting(hceServiceComponent) == targetState) return
        activity.packageManager.setComponentEnabledSetting(
            hceServiceComponent,
            targetState,
            PackageManager.DONT_KILL_APP,
        )
    }

    override fun enableReaderMode() {
        val currentAdapter = adapter ?: return
        if (!currentAdapter.isEnabled) return
        currentAdapter.enableReaderMode(
            activity,
            callback,
            READER_FLAGS,
            Bundle().apply {
                putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, PRESENCE_CHECK_DELAY_MS)
            },
        )
    }

    override fun disableReaderMode() {
        adapter?.disableReaderMode(activity)
    }

    companion object {
        const val PRESENCE_CHECK_DELAY_MS = 250
        val READER_FLAGS: Int = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
    }
}
