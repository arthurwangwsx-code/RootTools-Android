package com.arthur.roottools.policy

import com.arthur.roottools.model.RuntimePermissionRecord
import com.arthur.roottools.privilege.PrivilegeInputValidator

enum class RuntimePermissionMutationRejection {
    NOT_RUNTIME_DANGEROUS,
    INVALID_PACKAGE,
    INVALID_PERMISSION,
    PROTECTED_PACKAGE_REVOKE,
}

data class RuntimePermissionMutationDecision(
    val allowed: Boolean,
    val packageName: String? = null,
    val permissionName: String? = null,
    val rejection: RuntimePermissionMutationRejection? = null,
)

/** Pure safety gate for runtime permission grant/revoke operations. */
object RuntimePermissionMutationPolicy {
    fun evaluate(
        packageName: String,
        permission: RuntimePermissionRecord,
        granted: Boolean,
        protectedPackages: Set<String> = PackagePolicyController.PROTECTED_PACKAGES,
    ): RuntimePermissionMutationDecision {
        if (permission.protection != "dangerous") {
            return RuntimePermissionMutationDecision(false, rejection = RuntimePermissionMutationRejection.NOT_RUNTIME_DANGEROUS)
        }
        val pkg = PrivilegeInputValidator.packageName(packageName)
            ?: return RuntimePermissionMutationDecision(false, rejection = RuntimePermissionMutationRejection.INVALID_PACKAGE)
        val permissionName = PrivilegeInputValidator.permissionName(permission.name)
            ?: return RuntimePermissionMutationDecision(false, packageName = pkg, rejection = RuntimePermissionMutationRejection.INVALID_PERMISSION)
        if (!granted && pkg in protectedPackages) {
            return RuntimePermissionMutationDecision(
                false,
                packageName = pkg,
                permissionName = permissionName,
                rejection = RuntimePermissionMutationRejection.PROTECTED_PACKAGE_REVOKE,
            )
        }
        return RuntimePermissionMutationDecision(true, pkg, permissionName)
    }
}
