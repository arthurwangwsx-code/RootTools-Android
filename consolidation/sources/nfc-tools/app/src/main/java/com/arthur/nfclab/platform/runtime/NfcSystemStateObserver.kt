package com.arthur.nfclab.platform.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Build

class NfcSystemStateObserver(
    private val context: Context,
    private val adapter: NfcAdapter?,
    private val onEnabledChanged: (Boolean) -> Unit,
) {
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) return
            // Do not trust the broadcast extra as product state. Re-read the adapter so a spoofed
            // broadcast can never make the UI or runtime believe NFC is in a state it is not.
            onEnabledChanged(adapter?.isEnabled == true)
        }
    }

    fun start() {
        if (registered) return
        onEnabledChanged(adapter?.isEnabled == true)
        val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        registered = true
    }

    fun stop() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }
}
