package com.arthur.roottools.integration.termux

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

class DeveloperDeviceIdentityStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val deviceId: String
        get() {
            prefs.getString(KEY_DEVICE_ID, null)?.takeIf(DEVICE_ID_REGEX::matches)?.let { return it }
            val created = UUID.randomUUID().toString()
            prefs.edit { putString(KEY_DEVICE_ID, created) }
            return created
        }

    companion object {
        private const val PREFS = "developer_runtime_identity"
        private const val KEY_DEVICE_ID = "device_id"
        private val DEVICE_ID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}

