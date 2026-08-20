package com.arthur.roottools.data

import android.content.Context
import com.arthur.roottools.model.AdbBootPolicy
import com.arthur.roottools.model.AdbSnapshot

class AdbPreferenceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun bootPolicy(): AdbBootPolicy = AdbBootPolicy(
        restoreRootTcp = prefs.getBoolean(KEY_RESTORE_ROOT_TCP, false),
        restoreNativeWireless = prefs.getBoolean(KEY_RESTORE_NATIVE, false),
    )

    fun setBootPolicy(policy: AdbBootPolicy) {
        prefs.edit()
            .putBoolean(KEY_RESTORE_ROOT_TCP, policy.restoreRootTcp)
            .putBoolean(KEY_RESTORE_NATIVE, policy.restoreNativeWireless)
            .apply()
    }

    fun updateLastKnown(snapshot: AdbSnapshot) {
        prefs.edit()
            .putBoolean(KEY_LAST_ROOT_TCP, snapshot.rootTcpEnabled)
            .putInt(KEY_LAST_ROOT_PORT, snapshot.rootTcpPort ?: 5555)
            .putBoolean(KEY_LAST_NATIVE, snapshot.nativeWirelessEnabled)
            .putString(KEY_LAST_TAILSCALE, snapshot.tailscaleIpv4)
            .putString(KEY_LAST_LOCAL, snapshot.localIpv4)
            .putLong(KEY_LAST_UPDATED, snapshot.collectedAtMs)
            .apply()
    }

    fun lastKnown(): LastKnownAdbState = LastKnownAdbState(
        rootTcpEnabled = prefs.getBoolean(KEY_LAST_ROOT_TCP, false),
        rootTcpPort = prefs.getInt(KEY_LAST_ROOT_PORT, 5555),
        nativeWirelessEnabled = prefs.getBoolean(KEY_LAST_NATIVE, false),
        tailscaleIpv4 = prefs.getString(KEY_LAST_TAILSCALE, null),
        localIpv4 = prefs.getString(KEY_LAST_LOCAL, null),
        updatedAtMs = prefs.getLong(KEY_LAST_UPDATED, 0L),
    )

    data class LastKnownAdbState(
        val rootTcpEnabled: Boolean,
        val rootTcpPort: Int,
        val nativeWirelessEnabled: Boolean,
        val tailscaleIpv4: String?,
        val localIpv4: String?,
        val updatedAtMs: Long,
    )

    private companion object {
        const val PREFS = "adb-control"
        const val KEY_RESTORE_ROOT_TCP = "restore_root_tcp"
        const val KEY_RESTORE_NATIVE = "restore_native_wireless"
        const val KEY_LAST_ROOT_TCP = "last_root_tcp"
        const val KEY_LAST_ROOT_PORT = "last_root_port"
        const val KEY_LAST_NATIVE = "last_native"
        const val KEY_LAST_TAILSCALE = "last_tailscale"
        const val KEY_LAST_LOCAL = "last_local"
        const val KEY_LAST_UPDATED = "last_updated"
    }
}

