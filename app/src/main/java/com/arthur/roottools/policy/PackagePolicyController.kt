package com.arthur.roottools.policy

import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.model.PackageActionResult
import com.arthur.roottools.privilege.PrivilegeResult
import com.arthur.roottools.privilege.PrivilegeRouter

/**
 * Package governance Controller. Framework mutations have exactly one entry point and are routed
 * through Shizuku/Sui first with a safe RootShell fallback inside [PrivilegeRouter].
 */
class PackagePolicyController(
    private val router: PrivilegeRouter,
    private val auditStore: RootActionAuditStore? = null,
    private val auditSource: String = "internal",
) {
    suspend fun freeze(packageName: String): PackageActionResult {
        val decision = PackageMutationPolicy.evaluate(packageName, PackageMutationKind.FREEZE)
        if (!decision.allowed) return rejected(decision, "冻结")
        val pkg = requireNotNull(decision.packageName)
        val before = router.getPackageEnabledState(pkg).value ?: "unknown"
        return finish(
            result = router.setPackageEnabled(pkg, false),
            successMessage = "已冻结",
            failureMessage = "冻结失败",
            action = "freeze",
            target = pkg,
            before = before,
            after = "disabled-user",
            rollbackHint = "Enable $pkg",
        )
    }

    suspend fun enable(packageName: String): PackageActionResult {
        val decision = PackageMutationPolicy.evaluate(packageName, PackageMutationKind.ENABLE)
        if (!decision.allowed) return rejected(decision, "启用")
        val pkg = requireNotNull(decision.packageName)
        val before = router.getPackageEnabledState(pkg).value ?: "unknown"
        return finish(
            result = router.setPackageEnabled(pkg, true),
            successMessage = "已启用",
            failureMessage = "启用失败",
            action = "enable",
            target = pkg,
            before = before,
            after = "enabled",
            rollbackHint = if (before == "disabled-user") "Freeze $pkg" else "保持 enabled",
        )
    }

    suspend fun forceStop(packageName: String): PackageActionResult {
        val decision = PackageMutationPolicy.evaluate(packageName, PackageMutationKind.FORCE_STOP)
        if (!decision.allowed) return rejected(decision, "停止")
        val pkg = requireNotNull(decision.packageName)
        val before = when (router.isPackageRunning(pkg).value) {
            true -> "running"
            false -> "not-running"
            null -> "unknown"
        }
        return finish(
            result = router.forceStop(pkg),
            successMessage = "已停止",
            failureMessage = "停止失败",
            action = "force_stop",
            target = pkg,
            before = before,
            after = "stopped",
            rollbackHint = "手工重新打开应用",
        )
    }

    suspend fun setStandbyBucket(packageName: String, bucket: Int): PackageActionResult {
        val decision = PackageMutationPolicy.evaluate(
            packageName,
            PackageMutationKind.SET_STANDBY_BUCKET,
            bucket = bucket,
        )
        if (!decision.allowed) return rejected(decision, "后台档位")
        val pkg = requireNotNull(decision.packageName)
        val safeBucket = requireNotNull(decision.bucket)
        val beforeRaw = router.getStandbyBucket(pkg).value.orEmpty()
        val before = beforeRaw.substringAfter(':', beforeRaw).trim().ifBlank { "unknown" }
        return finish(
            result = router.setStandbyBucket(pkg, safeBucket),
            successMessage = "后台档位已更新",
            failureMessage = "后台档位更新失败",
            action = "standby_bucket",
            target = pkg,
            before = before,
            after = safeBucket.toString(),
            rollbackHint = before.toIntOrNull()?.let { "恢复 bucket $it" }.orEmpty(),
        )
    }

    suspend fun setBackgroundAllowed(packageName: String, allowed: Boolean): PackageActionResult {
        val decision = PackageMutationPolicy.evaluate(
            packageName,
            PackageMutationKind.SET_BACKGROUND_ALLOWED,
            backgroundAllowed = allowed,
        )
        if (!decision.allowed) return rejected(decision, "后台策略")
        val pkg = requireNotNull(decision.packageName)
        val before = router.getAppOp(pkg, "RUN_IN_BACKGROUND").value.orEmpty().take(240).ifBlank { "unknown" }
        val result = router.setBackgroundAllowed(pkg, allowed)
        return finish(
            result = result,
            successMessage = if (allowed) "后台运行已允许" else "后台运行已限制",
            failureMessage = "AppOps 更新失败",
            action = "background_appops",
            target = pkg,
            before = before,
            after = if (allowed) "allow" else "ignore",
            rollbackHint = "按前值恢复 RUN_IN_BACKGROUND",
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

    private fun rejected(decision: PackageMutationDecision, operation: String): PackageActionResult {
        val message = when (decision.rejection) {
            PackageMutationRejection.INVALID_PACKAGE -> "非法 package name"
            PackageMutationRejection.INVALID_BUCKET -> "不支持的 Standby bucket"
            PackageMutationRejection.PROTECTED_PACKAGE -> "受保护基础设施不能执行$operation"
            null -> "$operation 被安全策略拒绝"
        }
        return PackageActionResult(false, message)
    }

    companion object {
        val PROTECTED_PACKAGES = setOf(
            "com.arthur.roottools",
            "com.tailscale.ipn",
            "com.arthur.aibox.android.rootlab",
            "com.arlosoft.macrodroid",
            "li.songe.gkd",
            "moe.shizuku.privileged.api",
        )
        private const val APPIUM_PACKAGE = "io.appium.settings"
    }
}
