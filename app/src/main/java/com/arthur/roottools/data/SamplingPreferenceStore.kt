package com.arthur.roottools.data

import android.content.Context
import androidx.core.content.edit

class SamplingPreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("root_tools_sampling", Context.MODE_PRIVATE)

    var detailSeconds: Int
        get() = prefs.getInt(KEY_DETAIL_SECONDS, DEFAULT_DETAIL_SECONDS).takeIf { it in ALLOWED_SECONDS }
            ?: DEFAULT_DETAIL_SECONDS
        set(value) {
            require(value in ALLOWED_SECONDS) { "Unsupported sampling interval: $value" }
            prefs.edit { putInt(KEY_DETAIL_SECONDS, value) }
        }

    companion object {
        val ALLOWED_SECONDS = setOf(1, 2, 5)
        const val DEFAULT_DETAIL_SECONDS = 2
        private const val KEY_DETAIL_SECONDS = "detail_seconds"
    }
}
