package com.arthur.roottools.automation

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

class ActionTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("automation_api", Context.MODE_PRIVATE)

    val token: String
        get() {
            val existing = prefs.getString(KEY_TOKEN, null)
            if (!existing.isNullOrBlank()) return existing
            val generated = UUID.randomUUID().toString().replace("-", "")
            prefs.edit { putString(KEY_TOKEN, generated) }
            return generated
        }

    fun matches(candidate: String?): Boolean = candidate != null && candidate.length >= 24 && candidate == token

    private companion object {
        const val KEY_TOKEN = "token"
    }
}
