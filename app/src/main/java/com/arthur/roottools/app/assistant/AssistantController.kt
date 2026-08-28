package com.arthur.roottools.app.assistant

import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.feature.assistant.data.AssistantRepository
import com.arthur.roottools.feature.assistant.model.AssistantSwitchResult
import com.arthur.roottools.feature.assistant.model.AssistantSwitchStatus
import com.arthur.roottools.feature.assistant.policy.AssistantSelectionDecision
import com.arthur.roottools.feature.assistant.policy.AssistantSelectionPolicy
import com.arthur.roottools.feature.assistant.policy.AssistantSelectionRejectReason
import com.arthur.roottools.model.PrivilegeRouteBackend
import com.arthur.roottools.privilege.PrivilegeRouter

class AssistantController(
    private val repository: AssistantRepository,
    private val privilegeRouter: PrivilegeRouter,
    private val auditStore: RootActionAuditStore? = null,
    private val auditSource: String = "internal",
) {
    suspend fun switchTo(packageName: String): AssistantSwitchResult {
        val beforeSnapshot = repository.snapshot()
        val before = beforeSnapshot.currentPackage
        return when (
            val decision = AssistantSelectionPolicy.decide(
                targetPackage = packageName,
                currentPackage = before,
                eligiblePackages = beforeSnapshot.candidates.mapTo(linkedSetOf()) { it.packageName },
            )
        ) {
            AssistantSelectionDecision.NoOp -> AssistantSwitchResult(
                status = AssistantSwitchStatus.ALREADY_SELECTED,
                previousPackage = before,
                currentPackage = before,
            )
            is AssistantSelectionDecision.Reject -> AssistantSwitchResult(
                status = when (decision.reason) {
                    AssistantSelectionRejectReason.INVALID_PACKAGE -> AssistantSwitchStatus.INVALID_PACKAGE
                    AssistantSelectionRejectReason.NOT_ELIGIBLE -> AssistantSwitchStatus.NOT_ELIGIBLE
                },
                previousPackage = before,
                currentPackage = before,
            )
            is AssistantSelectionDecision.Switch -> performSwitch(before, decision.packageName)
        }
    }

    private suspend fun performSwitch(before: String?, target: String): AssistantSwitchResult {
        val write = privilegeRouter.setAssistantRoleHolder(target)
        if (!write.success) {
            recordAudit(before, target, targetAfter = null, backend = write.backend, success = false)
            return AssistantSwitchResult(
                status = AssistantSwitchStatus.WRITE_FAILED,
                backend = write.backend,
                previousPackage = before,
                currentPackage = before,
                detail = write.detail,
            )
        }

        val verify = privilegeRouter.getAssistantRoleHolder()
        val after = verify.value?.trim()?.ifBlank { null }
        val verified = verify.success && after == target
        recordAudit(before, target, after, write.backend, verified)
        return AssistantSwitchResult(
            status = if (verified) AssistantSwitchStatus.SWITCHED else AssistantSwitchStatus.VERIFY_FAILED,
            backend = write.backend,
            previousPackage = before,
            currentPackage = after,
            detail = if (verified) write.detail else verify.detail.ifBlank { "role holder mismatch" },
        )
    }

    private fun recordAudit(
        before: String?,
        target: String,
        targetAfter: String?,
        backend: PrivilegeRouteBackend,
        success: Boolean,
    ) {
        auditStore?.record(
            source = "$auditSource/${backend.displayName}",
            feature = "assistant-role",
            action = "set-default-assistant",
            target = target,
            before = before.orEmpty(),
            after = targetAfter.orEmpty().ifBlank { if (success) target else "failed" },
            success = success,
            rollbackHint = before?.let { "assistant-role:$it" }.orEmpty(),
        )
    }
}
