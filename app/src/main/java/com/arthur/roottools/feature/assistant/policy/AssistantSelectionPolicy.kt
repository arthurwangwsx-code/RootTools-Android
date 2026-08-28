package com.arthur.roottools.feature.assistant.policy

import com.arthur.roottools.privilege.PrivilegeInputValidator

sealed interface AssistantSelectionDecision {
    data class Switch(val packageName: String) : AssistantSelectionDecision
    data object NoOp : AssistantSelectionDecision
    data class Reject(val reason: AssistantSelectionRejectReason) : AssistantSelectionDecision
}

enum class AssistantSelectionRejectReason {
    INVALID_PACKAGE,
    NOT_ELIGIBLE,
}

/** Pure policy that keeps arbitrary package text away from privileged role mutation. */
object AssistantSelectionPolicy {
    fun decide(
        targetPackage: String,
        currentPackage: String?,
        eligiblePackages: Set<String>,
    ): AssistantSelectionDecision {
        val safeTarget = PrivilegeInputValidator.packageName(targetPackage)
            ?: return AssistantSelectionDecision.Reject(AssistantSelectionRejectReason.INVALID_PACKAGE)
        if (safeTarget !in eligiblePackages) {
            return AssistantSelectionDecision.Reject(AssistantSelectionRejectReason.NOT_ELIGIBLE)
        }
        return if (safeTarget == currentPackage) {
            AssistantSelectionDecision.NoOp
        } else {
            AssistantSelectionDecision.Switch(safeTarget)
        }
    }
}
