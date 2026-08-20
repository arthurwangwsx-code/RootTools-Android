package com.arthur.roottools.policy

import com.arthur.roottools.data.AppActionPlanStore
import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.model.AppActionPlan
import com.arthur.roottools.model.AppPlanExecutionResult
import com.arthur.roottools.model.AppPlanStatus
import com.arthur.roottools.model.AppPlanStep
import com.arthur.roottools.model.AppPlanStepKind
import com.arthur.roottools.model.AppPolicyProfileId
import com.arthur.roottools.model.BuiltInAppPolicyProfiles
import com.arthur.roottools.privilege.PrivilegeInputValidator
import com.arthur.roottools.privilege.PrivilegeRouter
import java.util.UUID

class AppBatchPolicyEngine(
    private val router: PrivilegeRouter,
    private val store: AppActionPlanStore,
    private val auditStore: RootActionAuditStore? = null,
) {
    suspend fun buildPlan(packages: Set<String>, profileId: AppPolicyProfileId): AppActionPlan {
        val profile = BuiltInAppPolicyProfiles.get(profileId)
        val safePackages = packages.mapNotNull(PrivilegeInputValidator::packageName).distinct().sorted()
        val warnings = mutableListOf<String>()
        val steps = mutableListOf<AppPlanStep>()

        safePackages.forEach { pkg ->
            if (pkg in PackagePolicyController.PROTECTED_PACKAGES && profileId != AppPolicyProfileId.ACTIVE) {
                warnings += "$pkg is protected and was skipped"
                return@forEach
            }
            profile.packageEnabled?.let { target ->
                val before = router.getPackageEnabledState(pkg).value ?: "unknown"
                val after = if (target) "enabled" else "disabled-user"
                if (before != after) steps += AppPlanStep(pkg, AppPlanStepKind.PACKAGE_ENABLED, "enabled", before, after)
            }
            profile.standbyBucket?.let { target ->
                val raw = router.getStandbyBucket(pkg).value.orEmpty()
                val before = raw.substringAfter(':', raw).trim().toIntOrNull()?.toString() ?: "unknown"
                if (before != target.toString()) steps += AppPlanStep(pkg, AppPlanStepKind.STANDBY_BUCKET, "standby", before, target.toString())
            }
            profile.runInBackgroundMode?.let { target ->
                val before = parseAppOpMode(router.getAppOp(pkg, "RUN_IN_BACKGROUND").value.orEmpty())
                if (before != target) steps += AppPlanStep(pkg, AppPlanStepKind.APP_OP, "RUN_IN_BACKGROUND", before, target)
            }
            profile.runAnyInBackgroundMode?.let { target ->
                val before = parseAppOpMode(router.getAppOp(pkg, "RUN_ANY_IN_BACKGROUND").value.orEmpty())
                if (before != target) steps += AppPlanStep(pkg, AppPlanStepKind.APP_OP, "RUN_ANY_IN_BACKGROUND", before, target)
            }
        }

        return AppActionPlan(
            id = UUID.randomUUID().toString(),
            createdAtMs = System.currentTimeMillis(),
            profileId = profileId,
            packages = safePackages,
            steps = steps,
            warnings = warnings,
        )
    }

    suspend fun apply(plan: AppActionPlan): AppPlanExecutionResult {
        val applied = mutableListOf<AppPlanStep>()
        for (step in plan.steps) {
            val success = applyStep(step, useAfter = true)
            if (!success) {
                val rolledBack = rollbackSteps(applied.asReversed())
                val status = if (rolledBack == applied.size) AppPlanStatus.FAILED_ROLLED_BACK else AppPlanStatus.FAILED_ROLLBACK_INCOMPLETE
                val failedPlan = plan.copy(status = status)
                store.append(failedPlan)
                return AppPlanExecutionResult(false, failedPlan, applied.size, rolledBack, "Failed at ${step.packageName}/${step.key}; rollback $rolledBack/${applied.size}")
            }
            applied += step
        }
        val appliedPlan = plan.copy(status = AppPlanStatus.APPLIED, appliedAtMs = System.currentTimeMillis())
        store.append(appliedPlan)
        return AppPlanExecutionResult(true, appliedPlan, applied.size, 0, "Applied ${applied.size} steps")
    }

    suspend fun rollback(plan: AppActionPlan): AppPlanExecutionResult {
        val rolledBack = rollbackSteps(plan.steps.asReversed())
        val success = rolledBack == plan.steps.size
        val updated = plan.copy(status = if (success) AppPlanStatus.ROLLED_BACK else AppPlanStatus.FAILED_ROLLBACK_INCOMPLETE)
        store.append(updated)
        return AppPlanExecutionResult(success, updated, 0, rolledBack, "Rollback $rolledBack/${plan.steps.size}")
    }

    fun lastApplied(): AppActionPlan? = store.lastApplied()

    private suspend fun rollbackSteps(steps: List<AppPlanStep>): Int {
        var count = 0
        for (step in steps) {
            if (!applyStep(step, useAfter = false)) break
            count++
        }
        return count
    }

    private suspend fun applyStep(step: AppPlanStep, useAfter: Boolean): Boolean {
        val value = if (useAfter) step.after else step.before
        val result = when (step.kind) {
            AppPlanStepKind.PACKAGE_ENABLED -> router.setPackageEnabled(step.packageName, value == "enabled")
            AppPlanStepKind.STANDBY_BUCKET -> value.toIntOrNull()?.let { router.setStandbyBucket(step.packageName, it) }
                ?: return false
            AppPlanStepKind.APP_OP -> router.setAppOp(step.packageName, step.key, value)
        }
        auditStore?.record(
            source = "Batch/${result.backend.displayName}",
            feature = "app_batch",
            action = if (useAfter) "apply_${step.kind.name.lowercase()}" else "rollback_${step.kind.name.lowercase()}",
            target = "${step.packageName}/${step.key}",
            before = if (useAfter) step.before else step.after,
            after = if (result.success) value else "failed",
            success = result.success,
            rollbackHint = "ActionPlan ${if (useAfter) "rollback" else "re-apply"}",
        )
        return result.success
    }

    private fun parseAppOpMode(raw: String): String {
        val match = Regex("\\b(allow|ignore|deny|default|foreground)\\b").find(raw.lowercase())
        return match?.groupValues?.get(1) ?: "default"
    }
}
