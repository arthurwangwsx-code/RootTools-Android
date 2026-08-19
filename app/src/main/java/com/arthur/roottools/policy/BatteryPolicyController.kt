package com.arthur.roottools.policy

import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.model.PackageActionResult
import com.arthur.roottools.root.RootShell

class BatteryPolicyController(
    private val shell: RootShell,
    private val auditStore: RootActionAuditStore? = null,
    private val auditSource: String = "internal",
) {
    suspend fun setProtection(enabled: Boolean, threshold: Int = 80): PackageActionResult {
        if (threshold !in 70..95) return PackageActionResult(false, "电池保护阈值超出安全范围")
        val beforeProtect = shell.execute("settings get global protect_battery", timeoutSeconds = 3).output.trim()
        val beforeThreshold = shell.execute("settings get global battery_protection_threshold", timeoutSeconds = 3).output.trim()
        val command = if (enabled) {
            "settings put global battery_protection_threshold $threshold; settings put global protect_battery 1"
        } else {
            "settings put global protect_battery 0"
        }
        val result = shell.execute(command, timeoutSeconds = 5)
        val afterProtect = shell.execute("settings get global protect_battery", timeoutSeconds = 3).output.trim()
        val afterThreshold = shell.execute("settings get global battery_protection_threshold", timeoutSeconds = 3).output.trim()
        auditStore?.record(
            source = auditSource,
            feature = "battery",
            action = "battery_protection",
            target = if (enabled) "$threshold%" else "off",
            before = "protect=$beforeProtect threshold=$beforeThreshold",
            after = "protect=$afterProtect threshold=$afterThreshold",
            success = result.success,
            rollbackHint = "恢复 protect_battery=$beforeProtect / threshold=$beforeThreshold",
        )
        return if (result.success) {
            PackageActionResult(true, if (enabled) "电池保护已开启：$threshold%" else "电池保护已关闭")
        } else {
            PackageActionResult(false, "电池保护修改失败：${result.output.take(120)}")
        }
    }
}
