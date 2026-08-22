package com.arthur.roottools.app.shadow

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import com.arthur.roottools.feature.shadow.ui.ShadowDisplayScreen

@Composable
fun ShadowDisplayRoute(
    onBack: () -> Unit,
    viewModel: ShadowDisplayViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShadowDisplayScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onStart = viewModel::start,
        onStop = viewModel::stop,
        onLaunchPackage = viewModel::launchPackage,
        onTap = viewModel::tap,
        onSwipe = viewModel::swipe,
        onTypeText = viewModel::typeText,
        onCapturePreview = viewModel::capturePreview,
        onDismissFeedback = viewModel::clearFeedback,
    )
}
