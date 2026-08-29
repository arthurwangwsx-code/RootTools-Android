package com.arthur.roottools.feature.network.inspection.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arthur.roottools.R
import com.arthur.roottools.feature.network.inspection.capture.AppTarget
import com.arthur.roottools.feature.network.inspection.capture.CaptureSession
import com.arthur.roottools.feature.network.inspection.capture.CaptureStatus
import com.arthur.roottools.feature.network.inspection.data.NetworkCaptureRepository

@Composable
fun NetworkCaptureRoute(
    onBack: () -> Unit,
    viewModel: NetworkCaptureViewModel = viewModel(),
) {
    val captureState by viewModel.state.collectAsStateWithLifecycle()
    NetworkCaptureScreen(
        state = captureState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSelectTarget = viewModel::selectTarget,
        onStart = viewModel::startCapture,
        onStop = viewModel::stopCapture,
    )
}

@Composable
private fun NetworkCaptureScreen(
    state: NetworkCaptureUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectTarget: (AppTarget?) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var confirmStart by remember { mutableStateOf(false) }
    val active = state.capture.active
    val filteredApps = remember(query, state.installedApps) {
        val needle = query.trim()
        state.installedApps.asSequence()
            .filter { needle.isBlank() || it.label.contains(needle, ignoreCase = true) || it.packageName.contains(needle, ignoreCase = true) }
            .take(50)
            .toList()
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text(stringResource(R.string.network_capture_confirm_title)) },
            text = { Text(stringResource(R.string.network_capture_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStart = false
                    onStart()
                }) { Text(stringResource(R.string.network_capture_start)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.network_capture_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.network_capture_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.loadingApps || state.runningAction) {
                        CircularProgressIndicator(modifier = Modifier.padding(12.dp))
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.network_capture_refresh))
                        }
                    }
                }
            }
            item { CaptureStatusCard(state) }
            if (active != null) {
                item { ActiveCaptureCard(active, state.runningAction, onStop) }
            } else if (state.capture.rootAvailable && state.capture.status != CaptureStatus.BACKEND_UNAVAILABLE) {
                item {
                    Text(
                        stringResource(R.string.network_capture_target_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item {
                    TargetCard(
                        selectedTarget = state.selectedTarget,
                        query = query,
                        onQueryChange = { query = it.take(128) },
                        onWholeDevice = { onSelectTarget(null) },
                    )
                }
                items(filteredApps, key = { "${it.uid}:${it.packageName}" }) { app ->
                    AppTargetCard(
                        target = app,
                        selected = state.selectedTarget == app,
                        enabled = !state.runningAction,
                        onSelect = { onSelectTarget(app) },
                    )
                }
                item {
                    Button(
                        onClick = { confirmStart = true },
                        enabled = !state.runningAction,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.network_capture_start))
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.network_capture_history_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (state.capture.sessions.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.network_capture_history_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.capture.sessions, key = CaptureSession::id) { session ->
                    CaptureSessionCard(session)
                }
            }
        }
    }
}

@Composable
private fun CaptureStatusCard(state: NetworkCaptureUiState) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                captureStatusText(state.capture.status),
                fontWeight = FontWeight.Bold,
                color = when (state.capture.status) {
                    CaptureStatus.ROOT_REQUIRED, CaptureStatus.BACKEND_UNAVAILABLE, CaptureStatus.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
            )
            Text(
                stringResource(R.string.network_capture_privacy_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun captureStatusText(status: CaptureStatus): String = stringResource(
    when (status) {
        CaptureStatus.IDLE -> R.string.network_capture_status_idle
        CaptureStatus.ROOT_REQUIRED -> R.string.network_capture_status_root_required
        CaptureStatus.BACKEND_UNAVAILABLE -> R.string.network_capture_status_backend_unavailable
        CaptureStatus.READY_PCAPD -> R.string.network_capture_status_ready_pcapd
        CaptureStatus.READY_TCPDUMP -> R.string.network_capture_status_ready_tcpdump
        CaptureStatus.ACTIVE_RECOVERED -> R.string.network_capture_status_active_recovered
        CaptureStatus.CAPTURING -> R.string.network_capture_status_capturing
        CaptureStatus.CAPTURE_COMPLETE -> R.string.network_capture_status_complete
        CaptureStatus.ERROR -> R.string.network_capture_status_error
    },
)

@Composable
private fun TargetCard(
    selectedTarget: AppTarget?,
    query: String,
    onQueryChange: (String) -> Unit,
    onWholeDevice: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = selectedTarget == null,
                onClick = onWholeDevice,
                label = { Text(stringResource(R.string.network_capture_whole_device)) },
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.network_capture_filter_apps)) },
            )
        }
    }
}

@Composable
private fun AppTargetCard(
    target: AppTarget,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    OutlinedButton(onClick = onSelect, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(target.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.network_capture_app_identity, target.packageName, target.uid),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) Text(stringResource(R.string.network_capture_selected), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActiveCaptureCard(session: CaptureSession, busy: Boolean, onStop: () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.network_capture_active_title), fontWeight = FontWeight.Bold)
            Text(session.appLabel)
            Text(session.packageName, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onStop, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.network_capture_stop))
            }
        }
    }
}

@Composable
private fun CaptureSessionCard(session: CaptureSession) {
    val analysis = session.analysis
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(session.appLabel, fontWeight = FontWeight.SemiBold)
            Text(session.packageName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(
                    R.string.network_capture_session_summary,
                    analysis?.packetCount ?: 0,
                    NetworkCaptureRepository.formatBytes(analysis?.byteCount ?: 0),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val protocols = analysis?.protocols?.joinToString(" · ") { "${it.protocol} ${it.packets}" }.orEmpty()
            if (protocols.isNotBlank()) {
                Text(protocols, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
