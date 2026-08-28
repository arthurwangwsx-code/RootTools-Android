package com.arthur.roottools.app.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arthur.roottools.feature.assistant.ui.AssistantSettingsScreen

@Composable
fun AssistantSettingsRoute(
    onBack: () -> Unit,
    viewModel: AssistantSettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AssistantSettingsScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSwitch = viewModel::switchTo,
        onDismissFeedback = viewModel::dismissFeedback,
    )
}
