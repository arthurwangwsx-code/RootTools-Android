package com.aibox.backgroundserver.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = RuntimePreferences(context)
        if (prefs.restoreAfterBoot && (prefs.screenOffWorkEnabled || prefs.wireGuardRequested)) {
            BackgroundRuntimeService.restore(context)
        }
    }
}
