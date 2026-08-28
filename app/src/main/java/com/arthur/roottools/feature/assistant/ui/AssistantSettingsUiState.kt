package com.arthur.roottools.feature.assistant.ui

import com.arthur.roottools.feature.assistant.model.AssistantSnapshot
import com.arthur.roottools.feature.assistant.model.AssistantSwitchResult

data class AssistantSettingsUiState(
    val loading: Boolean = true,
    val switchingPackage: String? = null,
    val snapshot: AssistantSnapshot = AssistantSnapshot(),
    val feedback: AssistantSwitchResult? = null,
    val loadFailure: String? = null,
)
