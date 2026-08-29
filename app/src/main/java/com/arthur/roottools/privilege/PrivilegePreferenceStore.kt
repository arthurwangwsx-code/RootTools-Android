package com.arthur.roottools.privilege

import android.content.Context
import com.arthur.roottools.model.FrameworkPrivilegePreference

class PrivilegePreferenceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var frameworkPreference: FrameworkPrivilegePreference
        get() = prefs.getString(KEY_FRAMEWORK_BACKEND, null)
            ?.let { runCatching { FrameworkPrivilegePreference.valueOf(it) }.getOrNull() }
            ?: FrameworkPrivilegePreference.AUTO
        set(value) {
            prefs.edit().putString(KEY_FRAMEWORK_BACKEND, value.name).apply()
        }

    private companion object {
        const val PREFS_NAME = "privilege_backend_preferences"
        const val KEY_FRAMEWORK_BACKEND = "framework_backend"
    }
}
