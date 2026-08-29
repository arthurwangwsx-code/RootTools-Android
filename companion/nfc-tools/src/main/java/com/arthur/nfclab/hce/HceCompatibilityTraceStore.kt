package com.arthur.nfclab.hce

import android.content.Context
import androidx.core.content.edit

data class HceCompatibilityTrace(
    val frameCount: Int = 0,
    val firstFrameAtMs: Long? = null,
    val lastFrameAtMs: Long? = null,
    val deactivatedAtMs: Long? = null,
    val deactivationReason: Int? = null,
)

class HceCompatibilityTraceStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun clear() {
        prefs.edit { clear() }
    }

    @Synchronized
    fun recordFrame() {
        val now = System.currentTimeMillis()
        val count = prefs.getInt(KEY_COUNT, 0)
        prefs.edit {
            putInt(KEY_COUNT, count + 1)
            if (!prefs.contains(KEY_FIRST)) putLong(KEY_FIRST, now)
            putLong(KEY_LAST, now)
            remove(KEY_DEACTIVATED)
            remove(KEY_DEACTIVATION_REASON)
        }
    }

    @Synchronized
    fun recordDeactivated(reason: Int) {
        prefs.edit {
            putLong(KEY_DEACTIVATED, System.currentTimeMillis())
            putInt(KEY_DEACTIVATION_REASON, reason)
        }
    }

    @Synchronized
    fun load(): HceCompatibilityTrace = HceCompatibilityTrace(
        frameCount = prefs.getInt(KEY_COUNT, 0),
        firstFrameAtMs = prefs.getLongOrNull(KEY_FIRST),
        lastFrameAtMs = prefs.getLongOrNull(KEY_LAST),
        deactivatedAtMs = prefs.getLongOrNull(KEY_DEACTIVATED),
        deactivationReason = if (prefs.contains(KEY_DEACTIVATION_REASON)) {
            prefs.getInt(KEY_DEACTIVATION_REASON, 0)
        } else {
            null
        },
    )

    private fun android.content.SharedPreferences.getLongOrNull(key: String): Long? =
        if (contains(key)) getLong(key, 0L) else null

    companion object {
        private const val PREFS = "hce_compat_trace"
        private const val KEY_COUNT = "count"
        private const val KEY_FIRST = "first"
        private const val KEY_LAST = "last"
        private const val KEY_DEACTIVATED = "deactivated"
        private const val KEY_DEACTIVATION_REASON = "deactivation_reason"
    }
}
