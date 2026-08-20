package com.arthur.roottools.policy

import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.model.PackageActionResult
import com.arthur.roottools.privilege.PrivilegeRouter

class AppOpsPolicyController(
    private val router: PrivilegeRouter,
    private val auditStore: RootActionAuditStore? = null,
    private val auditSource: String = "UI",
) {
    suspend fun setMode(packageName: String, op: String, mode: String): PackageActionResult {
        val decision = AppOpsMutationPolicy.evaluate(packageName, op, mode)
        if (!decision.allowed) {
            val message = when (decision.rejection) {
                AppOpsMutationRejection.INVALID_PACKAGE -> "非法 package name"
                AppOpsMutationRejection.UNSUPPORTED_OP -> "不支持的 AppOp"
                AppOpsMutationRejection.UNSUPPORTED_MODE -> "不支持的 AppOp mode"
                AppOpsMutationRejection.PROTECTED_PACKAGE_RESTRICTION -> "受保护基础设施不能设置 deny/ignore"
                null -> "AppOps 操作被安全策略拒绝"
            }
            return PackageActionResult(false, message)
        }
        val safePackage = requireNotNull(decision.packageName)
        val safeOp = requireNotNull(decision.op)
        val safeMode = requireNotNull(decision.mode)
        val before = router.getAppOp(safePackage, safeOp).value.orEmpty().take(280)
        val result = router.setAppOp(safePackage, safeOp, safeMode)
        auditStore?.record(
            source = "$auditSource/${result.backend.displayName}",
            feature = "appops",
            action = "set_appop",
            target = "$safePackage/$safeOp",
            before = before,
            after = if (result.success) safeMode else "failed",
            success = result.success,
            rollbackHint = "按前值恢复 $safeOp",
        )
        return if (result.success) {
            PackageActionResult(true, "$safeOp → $safeMode · ${result.backend.displayName}")
        } else {
            PackageActionResult(false, "AppOps 修改失败：${result.detail.take(160)}")
        }
    }
}
