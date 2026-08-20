package com.arthur.roottools.policy

import android.content.Context
import com.arthur.roottools.data.AdbPreferenceStore
import com.arthur.roottools.data.AdbRepository
import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.model.AdbActionResult
import com.arthur.roottools.model.AdbBootPolicy
import com.arthur.roottools.root.RootShell
import com.arthur.roottools.widget.AdbWidgetProvider

class AdbController(
    context: Context,
    private val shell: RootShell,
    private val auditStore: RootActionAuditStore? = null,
    private val auditSource: String = "internal",
) {
    private val appContext = context.applicationContext
    private val preferences = AdbPreferenceStore(appContext)
    private val repository = AdbRepository(appContext, shell)

    suspend fun setRootTcpEnabled(enabled: Boolean, port: Int = 5555): AdbActionResult {
        val before = repository.read()
        if (enabled && before.rootTcpEnabled && before.rootTcpPort == port) {
            AdbWidgetProvider.requestUpdate(appContext)
            return AdbActionResult(true, "Root ADB 已监听 $port，无需重启 adbd", before)
        }
        if (!enabled && !before.rootTcpEnabled) {
            AdbWidgetProvider.requestUpdate(appContext)
            return AdbActionResult(true, "Root ADB 已处于关闭状态", before)
        }
        val command = if (enabled) {
            """
                setprop service.adb.tcp.port $port
                stop adbd
                start adbd
                sleep 1
            """.trimIndent()
        } else {
            """
                setprop service.adb.tcp.port -1
                stop adbd
                start adbd
                sleep 1
            """.trimIndent()
        }
        val result = shell.execute(command, timeoutSeconds = 7)
        val after = repository.read()
        val success = result.success && if (enabled) after.rootTcpEnabled && after.rootTcpPort == port else !after.rootTcpEnabled
        auditStore?.record(
            source = auditSource,
            feature = "adb",
            action = if (enabled) "enable_root_tcp" else "disable_root_tcp",
            target = port.toString(),
            before = if (before.rootTcpEnabled) "${before.rootTcpPort}" else "off",
            after = if (after.rootTcpEnabled) "${after.rootTcpPort}" else "off",
            success = success,
            rollbackHint = if (enabled) "关闭 Root ADB TCP" else "重新开启 Root ADB TCP $port",
        )
        AdbWidgetProvider.requestUpdate(appContext)
        return AdbActionResult(
            success = success,
            message = when {
                success && enabled -> "Root ADB 已监听 $port"
                success -> "Root ADB 已关闭"
                else -> "Root ADB 切换失败，请检查 Root / adbd 状态"
            },
            snapshot = after,
        )
    }

    suspend fun setNativeWirelessEnabled(enabled: Boolean): AdbActionResult {
        val before = repository.read()
        if (!before.nativeWirelessSupported) {
            return AdbActionResult(false, "当前系统未报告 Wireless Debugging 支持", before)
        }
        if (before.nativeWirelessEnabled == enabled) {
            AdbWidgetProvider.requestUpdate(appContext)
            return AdbActionResult(
                true,
                if (enabled) "Android Wireless Debugging 已开启" else "Android Wireless Debugging 已关闭",
                before,
            )
        }
        val result = shell.execute(
            "settings put global adb_wifi_enabled ${if (enabled) 1 else 0}; sleep 1",
            timeoutSeconds = 5,
        )
        val after = repository.read()
        val success = result.success && after.nativeWirelessEnabled == enabled
        auditStore?.record(
            source = auditSource,
            feature = "adb",
            action = if (enabled) "enable_native_wireless" else "disable_native_wireless",
            before = if (before.nativeWirelessEnabled) "on" else "off",
            after = if (after.nativeWirelessEnabled) "on" else "off",
            success = success,
            rollbackHint = if (enabled) "关闭 Android Wireless Debugging" else "重新开启 Android Wireless Debugging",
        )
        AdbWidgetProvider.requestUpdate(appContext)
        return AdbActionResult(
            success,
            if (success) {
                if (enabled) "Android Wireless Debugging 已开启" else "Android Wireless Debugging 已关闭"
            } else {
                "Wireless Debugging 切换失败"
            },
            after,
        )
    }

    fun setBootPolicy(policy: AdbBootPolicy) {
        preferences.setBootPolicy(policy)
    }

    suspend fun restoreConfigured(): AdbActionResult {
        val policy = preferences.bootPolicy()
        if (!policy.restoreRootTcp && !policy.restoreNativeWireless) {
            return AdbActionResult(true, "未配置 ADB 开机恢复", repository.read())
        }
        var success = true
        if (policy.restoreRootTcp) success = setRootTcpEnabled(true).success && success
        if (policy.restoreNativeWireless) success = setNativeWirelessEnabled(true).success && success
        val snapshot = repository.read()
        return AdbActionResult(
            success = success,
            message = if (success) "ADB 开机恢复完成" else "ADB 开机恢复未完全成功",
            snapshot = snapshot,
        )
    }
}

