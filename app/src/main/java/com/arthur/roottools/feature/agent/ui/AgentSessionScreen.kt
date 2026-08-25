package com.arthur.roottools.feature.agent.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arthur.roottools.R
import com.arthur.roottools.app.agent.AgentSessionViewModel
import com.arthur.roottools.core.agent.AgentOverlayMode
import com.arthur.roottools.core.agent.AgentSessionStatus
import com.arthur.roottools.core.ui.component.RootToolsDetailHeader
import com.arthur.roottools.core.ui.component.RootToolsErrorCard
import com.arthur.roottools.core.ui.component.RootToolsKeyValueRow
import com.arthur.roottools.core.ui.component.RootToolsRiskBanner
import com.arthur.roottools.core.ui.component.RootToolsSectionCard
import com.arthur.roottools.core.ui.component.RootToolsSectionHeader
import com.arthur.roottools.core.ui.component.RootToolsStatusChip
import com.arthur.roottools.core.ui.token.RootToolsRiskLevel
import com.arthur.roottools.core.ui.token.RootToolsStatusTone

@Composable
fun AgentSessionScreen(
    viewModel: AgentSessionViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            RootToolsDetailHeader(
                title = stringResource(R.string.agent_session_title),
                subtitle = stringResource(R.string.agent_session_subtitle),
                onBack = onBack,
                loading = state.previewLoading,
                onRefresh = viewModel::capturePreview,
            )
        }

        item {
            RootToolsSectionCard {
                RootToolsSectionHeader(
                    title = state.session.title.ifBlank { stringResource(R.string.agent_session_no_task_title) },
                    subtitle = state.session.currentStep.ifBlank { stringResource(R.string.agent_session_idle_step) },
                )
                RootToolsStatusChip(
                    label = sessionStatusLabel(state.session.status),
                    tone = sessionStatusTone(state.session.status),
                )
                state.session.targetLabel?.let { RootToolsKeyValueRow(stringResource(R.string.agent_session_target), it) }
                state.session.progressTotal?.takeIf { it > 0 }?.let { total ->
                    RootToolsKeyValueRow(
                        stringResource(R.string.agent_session_progress),
                        stringResource(R.string.agent_session_progress_value, state.session.progressCurrent ?: 0, total),
                    )
                }
            }
        }

        item {
            RootToolsSectionCard {
                RootToolsSectionHeader(
                    title = stringResource(R.string.agent_overlay_permission_title),
                    subtitle = stringResource(
                        if (state.canDrawOverlays) R.string.agent_overlay_permission_granted
                        else R.string.agent_overlay_permission_required,
                    ),
                )
                RootToolsRiskBanner(
                    level = if (state.canDrawOverlays) RootToolsRiskLevel.Safe else RootToolsRiskLevel.Caution,
                    title = stringResource(
                        if (state.canDrawOverlays) R.string.agent_overlay_ready_title
                        else R.string.agent_overlay_fallback_title,
                    ),
                    detail = stringResource(
                        if (state.canDrawOverlays) R.string.agent_overlay_ready_detail
                        else R.string.agent_overlay_fallback_detail,
                    ),
                )
                if (!state.canDrawOverlays) {
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.agent_overlay_open_settings))
                    }
                }
            }
        }

        item {
            RootToolsSectionCard {
                RootToolsSectionHeader(
                    title = stringResource(R.string.agent_controls_title),
                    subtitle = stringResource(R.string.agent_controls_subtitle),
                )
                if (!state.session.active) {
                    Button(onClick = viewModel::startObserverSession, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.agent_start_observer))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::pauseResume, modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(
                                    if (state.session.status == AgentSessionStatus.PAUSED) R.string.agent_action_resume
                                    else R.string.agent_action_pause,
                                )
                            )
                        }
                        OutlinedButton(onClick = viewModel::stop, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.agent_action_stop))
                        }
                    }
                    if (state.canDrawOverlays) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.showOverlay(false) }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.agent_action_show_overlay))
                            }
                            OutlinedButton(onClick = { viewModel.showOverlay(true) }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.agent_action_expand_overlay))
                            }
                            if (state.session.overlayMode != AgentOverlayMode.HIDDEN) {
                                OutlinedButton(onClick = viewModel::hideOverlay, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.agent_action_hide))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            RootToolsSectionCard {
                RootToolsSectionHeader(
                    title = stringResource(R.string.agent_preview_title),
                    subtitle = stringResource(R.string.agent_preview_subtitle),
                )
                val bitmap = state.previewJpeg?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.agent_preview_title),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    )
                } else {
                    Text(
                        stringResource(R.string.agent_preview_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.previewError?.let { RootToolsErrorCard(it) }
                OutlinedButton(onClick = viewModel::capturePreview, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.agent_preview_refresh))
                }
            }
        }
    }
}

@Composable
private fun sessionStatusLabel(status: AgentSessionStatus): String = stringResource(
    when (status) {
        AgentSessionStatus.IDLE -> R.string.agent_status_idle
        AgentSessionStatus.RUNNING -> R.string.agent_status_running
        AgentSessionStatus.PAUSED -> R.string.agent_status_paused
        AgentSessionStatus.WAITING_USER -> R.string.agent_status_waiting
        AgentSessionStatus.COMPLETED -> R.string.agent_status_completed
        AgentSessionStatus.FAILED -> R.string.agent_status_failed
        AgentSessionStatus.STOPPED -> R.string.agent_status_stopped
    }
)

private fun sessionStatusTone(status: AgentSessionStatus): RootToolsStatusTone = when (status) {
    AgentSessionStatus.RUNNING -> RootToolsStatusTone.Success
    AgentSessionStatus.PAUSED -> RootToolsStatusTone.Neutral
    AgentSessionStatus.WAITING_USER -> RootToolsStatusTone.Warning
    AgentSessionStatus.FAILED -> RootToolsStatusTone.Danger
    AgentSessionStatus.COMPLETED -> RootToolsStatusTone.Success
    AgentSessionStatus.IDLE,
    AgentSessionStatus.STOPPED -> RootToolsStatusTone.Neutral
}
