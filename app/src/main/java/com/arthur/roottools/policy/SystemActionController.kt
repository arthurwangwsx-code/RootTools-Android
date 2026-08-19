package com.arthur.roottools.policy

import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.model.PackageActionResult
import com.arthur.roottools.model.SystemActionId
import com.arthur.roottools.root.RootShell

class SystemActionController(
    private val shell: RootShell,
    private val auditStore: RootActionAuditStore? = null,
    private val auditSource: String = "internal",
    private val batteryController: BatteryPolicyController? = null,
) {
    suspend fun run(action: SystemActionId): PackageActionResult {
        if (action == SystemActionId.BATTERY_PROTECTION_80) {
            return batteryController?.setProtection(true, 80)
                ?: PackageActionResult(false, "Battery controller unavailable")
        }
        val before = when (action) {
            SystemActionId.RESTART_ADBD -> "port=${shell.execute("getprop service.adb.tcp.port", timeoutSeconds = 3).output.trim()}"
            SystemActionId.RESTART_SYSTEM_UI -> "pid=${shell.execute("pidof com.android.systemui", timeoutSeconds = 3).output.trim()}"
            SystemActionId.STOP_BILIBILI -> shell.execute("pidof com.bilibili.app.in", timeoutSeconds = 3).output.trim().ifBlank { "not-running" }
            SystemActionId.BATTERY_PROTECTION_80 -> ""
        }
        val command = when (action) {
            SystemActionId.RESTART_ADBD -> "stop adbd; start adbd"
            SystemActionId.RESTART_SYSTEM_UI -> "pid=\$(pidof com.android.systemui); [ -n \"\$pid\" ] && kill -TERM \"\$pid\""
            SystemActionId.STOP_BILIBILI -> "am force-stop com.bilibili.app.in"
            SystemActionId.BATTERY_PROTECTION_80 -> error("delegated above")
        }
        val result = shell.execute(command, timeoutSeconds = 7)
        val after = when (action) {
            SystemActionId.RESTART_ADBD -> "port=${shell.execute("getprop service.adb.tcp.port", timeoutSeconds = 3).output.trim()}"
            SystemActionId.RESTART_SYSTEM_UI -> "pid=${shell.execute("pidof com.android.systemui", timeoutSeconds = 3).output.trim()}"
            SystemActionId.STOP_BILIBILI -> shell.execute("pidof com.bilibili.app.in", timeoutSeconds = 3).output.trim().ifBlank { "stopped" }
            SystemActionId.BATTERY_PROTECTION_80 -> ""
        }
        auditStore?.record(
            source = auditSource,
            feature = "system_actions",
            action = action.name.lowercase(),
            target = action.displayName,
            before = before,
            after = if (result.success) after else "failed",
            success = result.success,
            rollbackHint = when (action) {
                SystemActionId.RESTART_ADBD -> "adbd 会由 init 重新启动"
                SystemActionId.RESTART_SYSTEM_UI -> "SystemUI 会由系统重新拉起"
                SystemActionId.STOP_BILIBILI -> "手工重新打开 Bilibili"
                SystemActionId.BATTERY_PROTECTION_80 -> "在电池与温控页恢复之前保护设置"
            },
        )
        return if (result.success) {
            PackageActionResult(true, "${action.displayName} 已执行")
        } else {
            PackageActionResult(false, "${action.displayName} 失败：${result.output.take(160)}")
        }
    }
}
