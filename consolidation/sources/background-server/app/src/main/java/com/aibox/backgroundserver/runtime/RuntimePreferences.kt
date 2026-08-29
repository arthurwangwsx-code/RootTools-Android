package com.aibox.backgroundserver.runtime

import android.content.Context
import androidx.core.content.edit

class RuntimePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("background-runtime", Context.MODE_PRIVATE)

    var screenOffWorkEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_OFF_WORK, false)
        set(value) = prefs.edit { putBoolean(KEY_SCREEN_OFF_WORK, value) }

    var restoreAfterBoot: Boolean
        get() = prefs.getBoolean(KEY_RESTORE_AFTER_BOOT, false)
        set(value) = prefs.edit { putBoolean(KEY_RESTORE_AFTER_BOOT, value) }

    var wireGuardRequested: Boolean
        get() = prefs.getBoolean(KEY_WIREGUARD_REQUESTED, false)
        set(value) = prefs.edit { putBoolean(KEY_WIREGUARD_REQUESTED, value) }

    var screenOffWithoutLock: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_OFF_WITHOUT_LOCK, false)
        set(value) = prefs.edit { putBoolean(KEY_SCREEN_OFF_WITHOUT_LOCK, value) }

    companion object {
        private const val KEY_SCREEN_OFF_WORK = "screen-off-work"
        private const val KEY_RESTORE_AFTER_BOOT = "restore-after-boot"
        private const val KEY_WIREGUARD_REQUESTED = "wireguard-requested"
        private const val KEY_SCREEN_OFF_WITHOUT_LOCK = "screen-off-without-lock"
    }
}
