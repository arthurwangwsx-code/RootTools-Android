package com.arthur.roottools.policy

import com.arthur.roottools.privilege.PrivilegeInputValidator

enum class PackageMutationKind {
    FREEZE,
    ENABLE,
    FORCE_STOP,
    SET_STANDBY_BUCKET,
    SET_BACKGROUND_ALLOWED,
}

enum class PackageMutationRejection {
    INVALID_PACKAGE,
    INVALID_BUCKET,
    PROTECTED_PACKAGE,
}

data class PackageMutationDecision(
    val allowed: Boolean,
    val packageName: String? = null,
    val bucket: Int? = null,
    val rejection: PackageMutationRejection? = null,
)

/**
 * Pure safety contract for package mutations. The Controller owns user-facing messages and audit;
 * this object owns the invariants so they are testable without Android or a privileged backend.
 */
object PackageMutationPolicy {
    fun evaluate(
        packageName: String,
        action: PackageMutationKind,
        bucket: Int? = null,
        backgroundAllowed: Boolean? = null,
        protectedPackages: Set<String> = PackagePolicyController.PROTECTED_PACKAGES,
    ): PackageMutationDecision {
        val pkg = PrivilegeInputValidator.packageName(packageName)
            ?: return PackageMutationDecision(false, rejection = PackageMutationRejection.INVALID_PACKAGE)
        val safeBucket = if (action == PackageMutationKind.SET_STANDBY_BUCKET) {
            PrivilegeInputValidator.standbyBucket(bucket ?: -1)
                ?: return PackageMutationDecision(false, packageName = pkg, rejection = PackageMutationRejection.INVALID_BUCKET)
        } else null

        val protectedMutation = when (action) {
            PackageMutationKind.FREEZE,
            PackageMutationKind.FORCE_STOP,
            -> pkg in protectedPackages
            PackageMutationKind.SET_STANDBY_BUCKET -> pkg in protectedPackages && (safeBucket ?: 100) > 10
            PackageMutationKind.SET_BACKGROUND_ALLOWED -> pkg in protectedPackages && backgroundAllowed == false
            PackageMutationKind.ENABLE -> false
        }
        if (protectedMutation) {
            return PackageMutationDecision(
                false,
                packageName = pkg,
                bucket = safeBucket,
                rejection = PackageMutationRejection.PROTECTED_PACKAGE,
            )
        }
        return PackageMutationDecision(true, pkg, safeBucket)
    }
}
