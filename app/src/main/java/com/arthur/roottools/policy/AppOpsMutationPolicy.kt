package com.arthur.roottools.policy

import com.arthur.roottools.data.PermissionAppOpsRepository
import com.arthur.roottools.privilege.PrivilegeInputValidator

enum class AppOpsMutationRejection {
    INVALID_PACKAGE,
    UNSUPPORTED_OP,
    UNSUPPORTED_MODE,
    PROTECTED_PACKAGE_RESTRICTION,
}

data class AppOpsMutationDecision(
    val allowed: Boolean,
    val packageName: String? = null,
    val op: String? = null,
    val mode: String? = null,
    val rejection: AppOpsMutationRejection? = null,
)

/** Pure allow-list and protected-target policy for AppOps writes. */
object AppOpsMutationPolicy {
    fun evaluate(
        packageName: String,
        op: String,
        mode: String,
        protectedPackages: Set<String> = PackagePolicyController.PROTECTED_PACKAGES,
        supportedOps: Set<String> = PermissionAppOpsRepository.SUPPORTED_APP_OPS.toSet(),
        writableModes: Set<String> = PermissionAppOpsRepository.WRITABLE_MODES,
    ): AppOpsMutationDecision {
        val pkg = PrivilegeInputValidator.packageName(packageName)
            ?: return AppOpsMutationDecision(false, rejection = AppOpsMutationRejection.INVALID_PACKAGE)
        val safeOp = PrivilegeInputValidator.appOpName(op)?.takeIf { it in supportedOps }
            ?: return AppOpsMutationDecision(false, packageName = pkg, rejection = AppOpsMutationRejection.UNSUPPORTED_OP)
        val safeMode = PrivilegeInputValidator.appOpMode(mode)?.takeIf { it in writableModes }
            ?: return AppOpsMutationDecision(false, packageName = pkg, op = safeOp, rejection = AppOpsMutationRejection.UNSUPPORTED_MODE)
        if (pkg in protectedPackages && safeMode in setOf("ignore", "deny")) {
            return AppOpsMutationDecision(
                false,
                packageName = pkg,
                op = safeOp,
                mode = safeMode,
                rejection = AppOpsMutationRejection.PROTECTED_PACKAGE_RESTRICTION,
            )
        }
        return AppOpsMutationDecision(true, pkg, safeOp, safeMode)
    }
}
