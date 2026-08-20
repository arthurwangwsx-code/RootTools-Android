package com.arthur.roottools.policy

import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.model.PackageActionResult
import com.arthur.roottools.privilege.PrivilegeResult
import com.arthur.roottools.privilege.PrivilegeRouter
import com.arthur.roottools.privilege.PrivilegeInputValidator

class PackagePolicyController(
    private val router: PrivilegeRouter,
    private val auditStore: RootActionAuditStore? = null,
    private val auditSource: String = "internal",
) {
    suspend fun freeze(packageName: String): PackageActionResult {
        val safe = validate(packageName) ?: return invalid()
        if (safe in PROTECTED_PACKAGES) return PackageActionResult(false, "受保护基础设施不能冻结")
        val before = router.getPackageEnabledState(safe).value ?: "unknown"
        return finish(
            result = router.setPackageEnabled(safe, false),
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
        val before = router.getPackageEnabledState(safe).value ?: "unknown"
        return finish(
            result = router.setPackageEnabled(safe, true),
            successMessage = "已启用",
            failureMessage = "启用失败",
            action = "enable",
            target = safe,
            before = before,
            after = "enabled",
            rollbackHint = if (before == "disabled-user") "Freeze $safe" else "保持 enabled",
        )
    }

    suspend fun forceStop(packageName: String): PackageActionResult {
        val safe = validate(packageName) ?: return invalid()
        if (safe in PROTECTED_PACKAGES) return PackageActionResult(false, "受保护基础设施不能强制停止")
        val before = if (router.isPackageRunning(safe).value == true) "running" else "not-running"
        return finish(
            result = router.forceStop(safe),
            successMessage = "已停止",
            failureMessage = "停止失败",
            action = "force_stop",
            target = safe,
            before = before,
            after = "stopped",
            rollbackHint = "手工重新打开应用",
        )
    }

    suspend fun setStandbyBucket(packageName: String, bucket: Int): PackageActionResult {
        val safe = validate(packageName) ?: return invalid()
        if (bucket !in ALLOWED_BUCKETS) return PackageActionResult(false, "不支持的 Standby bucket")
        if (safe in PROTECTED_PACKAGES && bucket > 10) return PackageActionResult(false, "受保护基础设施不能降级到 Rare/Restricted")
        val beforeRaw = router.getStandbyBucket(safe).value.orEmpty().trim()
        val before = beforeRaw.substringAfter(':', beforeRaw).trim()
        return finish(
            result = router.setStandbyBucket(safe, bucket),
            successMessage = "后台档位已更新",
            failureMessage = "后台档位更新失败",
            action = "standby_bucket",
            target = safe,
            before = before,
            after = bucket.toString(),
            rollbackHint = before.toIntOrNull()?.let { "恢复 bucket $it" }.orEmpty(),
        )
    }

    suspend fun setBackgroundAllowed(packageName: String, allowed: Boolean): PackageActionResult {
        val safe = validate(packageName) ?: return invalid()
        if (!allowed && safe in PROTECTED_PACKAGES) return PackageActionResult(false, "受保护基础设施不能禁止后台")
        val before = router.getAppOp(safe, "RUN_IN_BACKGROUND").value.orEmpty().take(280)
        val mode = if (allowed) "allow" else "ignore"
        return finish(
            result = router.setBackgroundAllowed(safe, allowed),
            successMessage = if (allowed) "后台运行已允许" else "后台运行已限制",
            failureMessage = "AppOps 更新失败",
            action = "background_appops",
            target = safe,
            before = before,
            after = mode,
            rollbackHint = if (allowed) "按需恢复之前 AppOps" else "BG allow",
        )
    }

    suspend fun setAppiumTestMode(enabled: Boolean): PackageActionResult = finish(
        result = router.setAppiumTestMode(enabled),
        successMessage = if (enabled) "Appium 测试模式已开启" else "Appium 已恢复按需模式",
        failureMessage = "Appium 模式切换失败",
        action = "appium_test_mode",
        target = APPIUM_PACKAGE,
        before = if (enabled) "on-demand" else "test-mode",
        after = if (enabled) "test-mode" else "on-demand",
        rollbackHint = if (enabled) "关闭 Appium 测试模式" else "重新开启 Appium 测试模式",
    )

    private fun finish(
        result: PrivilegeResult<Unit>,
        successMessage: String,
        failureMessage: String,
        action: String,
        target: String,
        before: String,
        after: String,
        rollbackHint: String,
    ): PackageActionResult {
        auditStore?.record(
            source = "$auditSource/${result.backend.displayName}",
            feature = "packages",
            action = action,
            target = target,
            before = before,
            after = if (result.success) after else "failed",
            success = result.success,
            rollbackHint = rollbackHint,
        )
        return if (result.success) {
            PackageActionResult(true, "$successMessage · ${result.backend.displayName}")
        } else {
            PackageActionResult(false, "$failureMessage：${result.detail.take(160)}")
        }
    }

    private fun validate(packageName: String): String? = PrivilegeInputValidator.packageName(packageName)
    private fun invalid() = PackageActionResult(false, "非法 package name")

    companion object {
        val PROTECTED_PACKAGES = setOf(
            "com.arthur.roottools",
            "com.tailscale.ipn",
            "com.arthur.aibox.android.rootlab",
            "com.arlosoft.macrodroid",
            "li.songe.gkd",
            "moe.shizuku.privileged.api",
        )
        private val ALLOWED_BUCKETS = setOf(5, 10, 20, 30, 40, 45)
        private const val APPIUM_PACKAGE = "io.appium.settings"
    }
}
