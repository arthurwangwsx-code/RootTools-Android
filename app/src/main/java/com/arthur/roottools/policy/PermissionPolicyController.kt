package com.arthur.roottools.policy

import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.model.PackageActionResult
import com.arthur.roottools.model.RuntimePermissionRecord
import com.arthur.roottools.privilege.PrivilegeRouter

class PermissionPolicyController(
    private val router: PrivilegeRouter,
    private val auditStore: RootActionAuditStore? = null,
    private val auditSource: String = "UI",
) {
    suspend fun setGranted(packageName: String, permission: RuntimePermissionRecord, granted: Boolean): PackageActionResult {
        val decision = RuntimePermissionMutationPolicy.evaluate(packageName, permission, granted)
        if (!decision.allowed) {
            val message = when (decision.rejection) {
                RuntimePermissionMutationRejection.NOT_RUNTIME_DANGEROUS -> "仅 dangerous runtime permission 可修改"
                RuntimePermissionMutationRejection.INVALID_PACKAGE -> "非法 package name"
                RuntimePermissionMutationRejection.INVALID_PERMISSION -> "非法 permission name"
                RuntimePermissionMutationRejection.PROTECTED_PACKAGE_REVOKE -> "受保护基础设施不允许在普通入口 revoke runtime permission"
                null -> "Runtime permission 操作被安全策略拒绝"
            }
            return PackageActionResult(false, message)
        }
        val pkg = requireNotNull(decision.packageName)
        val permissionName = requireNotNull(decision.permissionName)
        val result = router.setRuntimePermission(pkg, permissionName, granted)
        auditStore?.record(
            source = "$auditSource/${result.backend.displayName}",
            feature = "permissions",
            action = if (granted) "grant_runtime" else "revoke_runtime",
            target = "$pkg/$permissionName",
            before = if (permission.granted) "granted" else "denied",
            after = if (result.success) if (granted) "granted" else "denied" else "failed",
            success = result.success,
            rollbackHint = "恢复为 ${if (permission.granted) "granted" else "denied"}",
        )
        return if (result.success) {
            PackageActionResult(true, "${permissionName.substringAfterLast('.')} → ${if (granted) "granted" else "denied"} · ${result.backend.displayName}")
        } else {
            PackageActionResult(false, "Runtime permission 修改失败：${result.detail.take(160)}")
        }
    }
}
