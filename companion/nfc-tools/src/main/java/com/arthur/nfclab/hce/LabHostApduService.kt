package com.arthur.nfclab.hce

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

class LabHostApduService : HostApduService() {
    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        HceCompatibilityTraceStore(this).recordFrame()
        val payload = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_PAYLOAD, DEFAULT_PAYLOAD)
            ?: DEFAULT_PAYLOAD
        val response = LabApduProtocol.respond(commandApdu, payload)
        Log.d(TAG, "HCE frame received")
        return response
    }

    override fun onDeactivated(reason: Int) {
        HceCompatibilityTraceStore(this).recordDeactivated(reason)
        Log.d(TAG, "HCE deactivated: $reason")
    }

    companion object {
        const val PREFS = "hce_lab"
        const val KEY_PAYLOAD = "payload"
        const val DEFAULT_PAYLOAD = "NFC Lab / authorized test credential"
        private const val TAG = "NfcLabHce"
    }
}

