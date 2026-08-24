package com.arthur.roottools.app.adgovernance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arthur.roottools.core.ui.action.openPackage
import com.arthur.roottools.feature.adgovernance.ui.AdGovernanceScreen

@Composable
fun AdGovernanceRoute(
    onBack: () -> Unit,
    viewModel: AdGovernanceViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AdGovernanceScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onOpenGkd = { openPackage(context, GKD_PACKAGE) },
        onOpenAdAway = { openPackage(context, ADAWAY_PACKAGE) },
    )
}

private const val GKD_PACKAGE = "li.songe.gkd"
private const val ADAWAY_PACKAGE = "org.adaway"
