package com.arthur.roottools.feature.assistant.model

import com.arthur.roottools.model.PrivilegeRouteBackend

data class AssistantCandidate(
    val packageName: String,
    val label: String,
    val voiceServiceComponents: List<String>,
)

enum class PowerKeyAssistantBinding {
    ASSISTANT,
    OTHER,
    UNKNOWN,
}

data class PowerKeyAssistantState(
    val binding: PowerKeyAssistantBinding = PowerKeyAssistantBinding.UNKNOWN,
    val oemLongPressValue: String? = null,
    val aospLongPressValue: String? = null,
    val aospVeryLongPressValue: String? = null,
)

data class AssistantSnapshot(
    val currentPackage: String? = null,
    val candidates: List<AssistantCandidate> = emptyList(),
    val powerKey: PowerKeyAssistantState = PowerKeyAssistantState(),
    val readBackend: PrivilegeRouteBackend = PrivilegeRouteBackend.NONE,
    val readError: String? = null,
) {
    val currentCandidate: AssistantCandidate?
        get() = candidates.firstOrNull { it.packageName == currentPackage }
}

enum class AssistantSwitchStatus {
    SWITCHED,
    ALREADY_SELECTED,
    INVALID_PACKAGE,
    NOT_ELIGIBLE,
    WRITE_FAILED,
    VERIFY_FAILED,
}

data class AssistantSwitchResult(
    val status: AssistantSwitchStatus,
    val backend: PrivilegeRouteBackend = PrivilegeRouteBackend.NONE,
    val previousPackage: String? = null,
    val currentPackage: String? = null,
    val detail: String = "",
) {
    val success: Boolean
        get() = status == AssistantSwitchStatus.SWITCHED || status == AssistantSwitchStatus.ALREADY_SELECTED
}
