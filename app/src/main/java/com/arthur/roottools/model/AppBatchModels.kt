package com.arthur.roottools.model

enum class AppPolicyProfileId(val displayName: String) {
    ACTIVE("Active"),
    RARE("Rare"),
    RESTRICTED("Restricted"),
    ON_DEMAND("On-demand"),
    FREEZE("Freeze"),
}

data class AppPolicyProfile(
    val id: AppPolicyProfileId,
    val packageEnabled: Boolean? = null,
    val standbyBucket: Int? = null,
    val runInBackgroundMode: String? = null,
    val runAnyInBackgroundMode: String? = null,
)

enum class AppPlanStepKind {
    PACKAGE_ENABLED,
    STANDBY_BUCKET,
    APP_OP,
}

data class AppPlanStep(
    val packageName: String,
    val kind: AppPlanStepKind,
    val key: String,
    val before: String,
    val after: String,
)

enum class AppPlanStatus {
    PREVIEW,
    APPLIED,
    ROLLED_BACK,
    FAILED_ROLLED_BACK,
    FAILED_ROLLBACK_INCOMPLETE,
}

data class AppActionPlan(
    val id: String,
    val createdAtMs: Long,
    val profileId: AppPolicyProfileId,
    val packages: List<String>,
    val steps: List<AppPlanStep>,
    val warnings: List<String> = emptyList(),
    val status: AppPlanStatus = AppPlanStatus.PREVIEW,
    val appliedAtMs: Long? = null,
)

data class AppPlanExecutionResult(
    val success: Boolean,
    val plan: AppActionPlan,
    val appliedSteps: Int,
    val rolledBackSteps: Int,
    val message: String,
)

object BuiltInAppPolicyProfiles {
    val all: List<AppPolicyProfile> = listOf(
        AppPolicyProfile(
            AppPolicyProfileId.ACTIVE,
            packageEnabled = true,
            standbyBucket = 10,
            runInBackgroundMode = "allow",
            runAnyInBackgroundMode = "allow",
        ),
        AppPolicyProfile(
            AppPolicyProfileId.RARE,
            packageEnabled = true,
            standbyBucket = 40,
        ),
        AppPolicyProfile(
            AppPolicyProfileId.RESTRICTED,
            packageEnabled = true,
            standbyBucket = 45,
            runInBackgroundMode = "ignore",
            runAnyInBackgroundMode = "ignore",
        ),
        AppPolicyProfile(
            AppPolicyProfileId.ON_DEMAND,
            packageEnabled = true,
            standbyBucket = 45,
            runInBackgroundMode = "ignore",
            runAnyInBackgroundMode = "ignore",
        ),
        AppPolicyProfile(
            AppPolicyProfileId.FREEZE,
            packageEnabled = false,
        ),
    )

    fun get(id: AppPolicyProfileId): AppPolicyProfile = all.first { it.id == id }
}
