package com.arthur.roottools.privilege

import android.content.Context
import android.content.pm.PackageManager
import com.arthur.roottools.model.PrivilegeBackendType
import com.arthur.roottools.model.ShizukuBridgeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import rikka.sui.Sui

class ShizukuBridge(context: Context) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<ShizukuBridgeState> = _state.asStateFlow()

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        refresh()
    }
    private val binderDead = Shizuku.OnBinderDeadListener {
        val previous = _state.value
        _state.value = previous.copy(
            binderAlive = false,
            permissionGranted = false,
            backend = PrivilegeBackendType.NONE,
            uid = null,
            lastBinderDeathAt = System.currentTimeMillis(),
            error = "Shizuku binder disconnected",
        )
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ ->
        refresh()
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun refresh() {
        _state.value = readState(lastDeath = _state.value.lastBinderDeathAt)
    }

    fun requestPermission(requestCode: Int = REQUEST_CODE): Boolean {
        val current = readState(lastDeath = _state.value.lastBinderDeathAt)
        _state.value = current
        if (!current.binderAlive || current.permissionGranted || current.permissionDeniedPermanently) return false
        return runCatching {
            Shizuku.requestPermission(requestCode)
            true
        }.getOrElse {
            _state.value = current.copy(error = it.message ?: it.javaClass.simpleName)
            false
        }
    }

    fun close() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
    }

    private fun readState(lastDeath: Long? = null): ShizukuBridgeState {
        val managerInstalled = runCatching {
            appContext.packageManager.getPackageInfo(SHIZUKU_MANAGER, 0)
            true
        }.getOrDefault(false)
        val suiAvailable = runCatching { Sui.isSui() }.getOrDefault(false)
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            return ShizukuBridgeState(
                binderAlive = false,
                managerInstalled = managerInstalled,
                suiAvailable = suiAvailable,
                lastBinderDeathAt = lastDeath,
            )
        }
        return runCatching {
            val permission = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            val rationale = runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false)
            val uid = runCatching { Shizuku.getUid() }.getOrNull()
            val backend = when {
                suiAvailable && uid == 0 -> PrivilegeBackendType.SUI_ROOT
                uid == 0 -> PrivilegeBackendType.SHIZUKU_ROOT
                uid == 2000 -> PrivilegeBackendType.SHIZUKU_ADB
                else -> PrivilegeBackendType.NONE
            }
            ShizukuBridgeState(
                binderAlive = true,
                permissionGranted = permission,
                permissionDeniedPermanently = !permission && rationale,
                backend = backend,
                uid = uid,
                serverVersion = runCatching { Shizuku.getVersion() }.getOrNull(),
                serverPatchVersion = readPatchVersion(),
                selinuxContext = runCatching { Shizuku.getSELinuxContext() }.getOrNull(),
                managerInstalled = managerInstalled,
                suiAvailable = suiAvailable,
                lastBinderDeathAt = lastDeath,
            )
        }.getOrElse {
            ShizukuBridgeState(
                binderAlive = binderAlive,
                managerInstalled = managerInstalled,
                suiAvailable = suiAvailable,
                lastBinderDeathAt = lastDeath,
                error = it.message ?: it.javaClass.simpleName,
            )
        }
    }

    private fun readPatchVersion(): Int? = runCatching {
        Shizuku::class.java.getDeclaredMethod("getServerPatchVersion").apply { isAccessible = true }
            .invoke(null) as? Int
    }.getOrNull()

    companion object {
        const val REQUEST_CODE = 6101
        const val SHIZUKU_MANAGER = "moe.shizuku.privileged.api"
    }
}
