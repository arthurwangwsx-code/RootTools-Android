package com.arthur.roottools.policy

import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.model.PackageActionResult
import com.arthur.roottools.root.RootShell

class PackagePolicyController(
    private val shell: RootShell,
    private val auditStore: RootActionAuditStore? = null,
    private val auditSource: String = "internal",
) {
    suspend fun freeze(packageName: String): PackageActionResult {
        val safe = validate(packageName) ?: return invalid()
        if (safe in PROTECTED_PACKAGES) return PackageActionResult(false, "受保护基础设施不能冻结")
        val before = readPackageEnabledState(safe)
        return execute(
            command = "pm disable-user --user 0 $safe",
            successMessage = "已冻结",
            failureMessage = "冻结失败",
            action = "freeze",
            target = safe,
            before = before,
            after = "disabled-user",
            rollbackHint = "Enable $safe",
        )
    }

    suspend fun enable(packageName: String): PackageActionResult {
        val safe = validate(packageName) ?: return invalid()
        val before = readPackageEnabledState(safe)
        return execute(
            "pm enable $safe", "已启用", "启用失败",
            action = "enable", target = safe, before = before, after = "enabled",
            rollbackHint = if (before == "disabled-user") "Freeze $safe" else "保持 enabled",
        )
    }

    suspend fun forceStop(packageName: String): PackageActionResult {
        val safe = validate(packageName) ?: return invalid()
        if (safe in PROTECTED_PACKAGES) return PackageActionResult(false, "受保护基础设施不能强制停止")
        val before = shell.execute("pidof $safe 2>/dev/null", timeoutSeconds = 3).output.trim().ifBlank { "not-running" }
        return execute(
            "am force-stop $safe", "已停止", "停止失败",
            action = "force_stop", target = safe, before = before, after = "stopped",
            rollbackHint = "手工重新打开应用",
        )
    }

    suspend fun setStandbyBucket(packageName: String, bucket: Int): PackageActionResult {
        val safe = validate(packageName) ?: return invalid()
        if (bucket !in ALLOWED_BUCKETS) return PackageActionResult(false, "不支持的 Standby bucket")
        if (safe in PROTECTED_PACKAGES && bucket > 10) return PackageActionResult(false, "受保护基础设施不能降级到 Rare/Restricted")
        val beforeRaw = shell.execute("am get-standby-bucket $safe 2>/dev/null", timeoutSeconds = 3).output.trim()
        val before = beforeRaw.substringAfter(':', beforeRaw).trim()
        return execute(
            "am set-standby-bucket $safe $bucket", "后台档位已更新", "后台档位更新失败",
            action = "standby_bucket", target = safe, before = before, after = bucket.toString(),
            rollbackHint = before.toIntOrNull()?.let { "恢复 bucket $it" }.orEmpty(),
        )
    }

    suspend fun setBackgroundAllowed(packageName: String, allowed: Boolean): PackageActionResult {
        val safe = validate(packageName) ?: return invalid()
        if (!allowed && safe in PROTECTED_PACKAGES) return PackageActionResult(false, "受保护基础设施不能禁止后台")
        val mode = if (allowed) "allow" else "ignore"
        val command = "cmd appops set $safe RUN_IN_BACKGROUND $mode; cmd appops set $safe RUN_ANY_IN_BACKGROUND $mode"
        val before = shell.execute("cmd appops get $safe RUN_IN_BACKGROUND 2>/dev/null | head -n 4", timeoutSeconds = 3).output.trim()
        return execute(
            command, if (allowed) "后台运行已允许" else "后台运行已限制", "AppOps 更新失败",
            action = "background_appops", target = safe, before = before, after = mode,
            rollbackHint = if (allowed) "按需恢复之前 AppOps" else "BG allow",
        )
    }

    suspend fun setAppiumTestMode(enabled: Boolean): PackageActionResult {
        val command = if (enabled) {
            "pm enable $APPIUM_PACKAGE; cmd notification allow_listener $APPIUM_COMPONENT; dumpsys deviceidle whitelist +$APPIUM_PACKAGE"
        } else {
            "cmd notification disallow_listener $APPIUM_COMPONENT; dumpsys deviceidle whitelist -$APPIUM_PACKAGE; am set-standby-bucket $APPIUM_PACKAGE 30"
        }
        return execute(
            command,
            if (enabled) "Appium 测试模式已开启" else "Appium 已恢复按需模式",
            "Appium 模式切换失败",
            action = "appium_test_mode",
            target = APPIUM_PACKAGE,
            before = if (enabled) "on-demand" else "test-mode",
            after = if (enabled) "test-mode" else "on-demand",
            rollbackHint = if (enabled) "关闭 Appium 测试模式" else "重新开启 Appium 测试模式",
        )
    }

    private suspend fun execute(
        command: String,
        successMessage: String,
        failureMessage: String,
        action: String,
        target: String,
        before: String,
        after: String,
        rollbackHint: String,
    ): PackageActionResult {
        val result = shell.execute(command, timeoutSeconds = 8)
        auditStore?.record(
            source = auditSource,
            feature = "packages",
            action = action,
            target = target,
            before = before,
            after = if (result.success) after else "failed",
            success = result.success,
            rollbackHint = rollbackHint,
        )
        return if (result.success) PackageActionResult(true, successMessage) else PackageActionResult(false, "$failureMessage：${result.output.take(160)}")
    }

    private suspend fun readPackageEnabledState(packageName: String): String = shell.execute(
        "if pm list packages -d | grep -q '^package:$packageName$'; then echo disabled-user; else echo enabled; fi",
        timeoutSeconds = 4,
    ).output.trim().ifBlank { "unknown" }

    private fun validate(packageName: String): String? = packageName.takeIf { PACKAGE_REGEX.matches(it) }
    private fun invalid() = PackageActionResult(false, "非法 package name")

    companion object {
        val PROTECTED_PACKAGES = setOf(
            "com.arthur.roottools",
            "com.tailscale.ipn",
            "com.arthur.aibox.android.rootlab",
            "com.arlosoft.macrodroid",
            "li.songe.gkd",
        )
        private val ALLOWED_BUCKETS = setOf(5, 10, 20, 30, 40, 45)
        private val PACKAGE_REGEX = Regex("[A-Za-z0-9._]+")
        private const val APPIUM_PACKAGE = "io.appium.settings"
        private const val APPIUM_COMPONENT = "io.appium.settings/io.appium.settings.NLService"
    }
}
