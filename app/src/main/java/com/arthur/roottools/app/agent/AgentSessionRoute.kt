package com.arthur.roottools.app.agent

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arthur.roottools.feature.agent.ui.AgentSessionScreen

@Composable
fun AgentSessionRoute(onBack: () -> Unit) {
    val viewModel: AgentSessionViewModel = viewModel()
    AgentSessionScreen(viewModel = viewModel, onBack = onBack)
}
