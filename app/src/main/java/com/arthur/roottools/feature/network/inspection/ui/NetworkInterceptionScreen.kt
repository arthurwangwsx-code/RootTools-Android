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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.arthur.roottools.feature.network.inspection.intercept.CertificateSource
import com.arthur.roottools.feature.network.inspection.intercept.InterceptionOptions
import com.arthur.roottools.feature.network.inspection.intercept.InterceptionPhase
import com.arthur.roottools.feature.network.inspection.intercept.InterceptionSession
import com.arthur.roottools.feature.network.inspection.intercept.InterceptionStatus

@Composable
fun NetworkInterceptionRoute(
    onBack: () -> Unit,
    viewModel: NetworkInterceptionViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NetworkInterceptionScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSelectTarget = viewModel::selectTarget,
        onOptions = viewModel::updateOptions,
        onImportCertificate = viewModel::importAddonCertificate,
        onGenerateCertificate = viewModel::generateStandaloneCertificate,
        onInstallCertificate = viewModel::installCertificateModule,
        onRemoveCertificate = viewModel::removeCertificateModule,
        onOpenAddon = viewModel::openAddonPage,
        onOpenAddonSettings = viewModel::openAddonSettings,
        onCleanup = viewModel::cleanupRules,
        onStart = viewModel::startInterception,
        onStop = viewModel::stopInterception,
        onClearFailure = viewModel::clearActionFailure,
    )
}

@Composable
private fun NetworkInterceptionScreen(
    state: NetworkInterceptionUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectTarget: (AppTarget) -> Unit,
    onOptions: (InterceptionOptions) -> Unit,
    onImportCertificate: () -> Unit,
    onGenerateCertificate: () -> Unit,
    onInstallCertificate: () -> Unit,
    onRemoveCertificate: () -> Unit,
    onOpenAddon: () -> Unit,
    onOpenAddonSettings: () -> Unit,
    onCleanup: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClearFailure: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<InterceptionConfirmation?>(null) }
    val active = state.runtime.phase in setOf(
        InterceptionPhase.STARTING,
        InterceptionPhase.RUNNING,
        InterceptionPhase.STOPPING,
    )
    val filteredApps = remember(query, state.installedApps) {
        val needle = query.trim()
        state.installedApps.asSequence()
            .filter { needle.isBlank() || it.label.contains(needle, true) || it.packageName.contains(needle, true) }
            .take(40)
            .toList()
    }

    if (state.actionFailed) {
        AlertDialog(
            onDismissRequest = onClearFailure,
            title = { Text(stringResource(R.string.network_interception_action_failed_title)) },
            text = { Text(stringResource(R.string.network_interception_action_failed_body)) },
            confirmButton = {
                TextButton(onClick = onClearFailure) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
    confirmation?.let { requested ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(stringResource(requested.titleRes)) },
            text = { Text(stringResource(requested.bodyRes)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    when (requested) {
                        InterceptionConfirmation.START -> onStart()
                        InterceptionConfirmation.INSTALL_CA -> onInstallCertificate()
                        InterceptionConfirmation.REMOVE_CA -> onRemoveCertificate()
                    }
                }) { Text(stringResource(requested.actionRes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) {
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
                            stringResource(R.string.network_interception_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.network_interception_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.busy) CircularProgressIndicator(modifier = Modifier.padding(12.dp)) else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.network_interception_refresh))
                        }
                    }
                }
            }
            item { InterceptionStatusCard(state) }
            item {
                AddonSetupCard(
                    state = state,
                    onOpenAddon = onOpenAddon,
                    onOpenSettings = onOpenAddonSettings,
                    onImportCertificate = onImportCertificate,
                )
            }
            item {
                CertificateSetupCard(
                    state = state,
                    onGenerate = onGenerateCertificate,
                    onInstall = { confirmation = InterceptionConfirmation.INSTALL_CA },
                    onRemove = { confirmation = InterceptionConfirmation.REMOVE_CA },
                )
            }
            if (active) {
                item { ActiveInterceptionCard(state, onStop) }
                if (state.runtime.recentEvents.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.network_interception_recent_events)) }
                    items(state.runtime.recentEvents.take(30), key = { it.id }) { event ->
                        Card(shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(event.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(event.preview, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else {
                item { InterceptionOptionsCard(state.options, onOptions) }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(128) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.network_interception_filter_apps)) },
                    )
                }
                items(filteredApps, key = { "${it.uid}:${it.packageName}" }) { app ->
                    InterceptionTargetCard(
                        app = app,
                        selected = state.selectedTarget == app,
                        enabled = !state.busy,
                        onClick = { onSelectTarget(app) },
                    )
                }
                item {
                    Button(
                        onClick = { confirmation = InterceptionConfirmation.START },
                        enabled = state.selectedTarget != null && state.addon.installed &&
                            state.certificate.systemTrusted && state.certificate.source == CertificateSource.MITM_ADDON &&
                            !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.network_interception_start)) }
                }
            }
            item { SectionTitle(stringResource(R.string.network_interception_history)) }
            if (state.sessions.isEmpty()) {
                item { Text(stringResource(R.string.network_interception_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(state.sessions, key = InterceptionSession::id) { session -> SessionSummaryCard(session) }
            }
            item {
                OutlinedButton(onClick = onCleanup, enabled = !active && !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.network_interception_cleanup_rules))
                }
            }
        }
    }
}

@Composable
private fun InterceptionStatusCard(state: NetworkInterceptionUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(interceptionStatusText(state.runtime.status), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.network_interception_privacy_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddonSetupCard(
    state: NetworkInterceptionUiState,
    onOpenAddon: () -> Unit,
    onOpenSettings: () -> Unit,
    onImportCertificate: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.network_interception_addon_title), fontWeight = FontWeight.Bold)
            Text(
                if (state.addon.installed) {
                    stringResource(R.string.network_interception_addon_installed, state.addon.versionName.orEmpty())
                } else {
                    stringResource(R.string.network_interception_addon_missing)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenAddon, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.network_interception_open_addon))
                }
                if (state.addon.installed) {
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.network_interception_addon_settings))
                    }
                    Button(onClick = onImportCertificate, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.network_interception_import_ca))
                    }
                }
            }
        }
    }
}

@Composable
private fun CertificateSetupCard(
    state: NetworkInterceptionUiState,
    onGenerate: () -> Unit,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.network_interception_ca_title), fontWeight = FontWeight.Bold)
            Text(certificateStatusText(state), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.certificate.requiresReboot) {
                Text(stringResource(R.string.network_interception_ca_reboot), color = MaterialTheme.colorScheme.error)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onGenerate, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.network_interception_generate_ca))
                }
                if (state.certificate.available) {
                    Button(onClick = onInstall, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.network_interception_install_ca))
                    }
                }
                if (state.certificate.systemModuleInstalled) {
                    OutlinedButton(onClick = onRemove, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.network_interception_remove_ca))
                    }
                }
            }
            Text(
                stringResource(R.string.network_interception_standalone_ca_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InterceptionOptionsCard(options: InterceptionOptions, onOptions: (InterceptionOptions) -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.network_interception_options_title), fontWeight = FontWeight.Bold)
            OptionSwitch(R.string.network_interception_block_quic, options.blockQuic) { onOptions(options.copy(blockQuic = it)) }
            OptionSwitch(R.string.network_interception_restart_target, options.restartTarget) { onOptions(options.copy(restartTarget = it)) }
            OptionSwitch(R.string.network_interception_full_payload, options.fullPayload) { onOptions(options.copy(fullPayload = it)) }
            OptionSwitch(R.string.network_interception_insecure_upstream, options.sslInsecureUpstream) { onOptions(options.copy(sslInsecureUpstream = it)) }
        }
    }
}

@Composable
private fun OptionSwitch(labelRes: Int, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(labelRes), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun InterceptionTargetCard(app: AppTarget, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) Text(stringResource(R.string.network_capture_selected), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActiveInterceptionCard(state: NetworkInterceptionUiState, onStop: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.network_interception_active), fontWeight = FontWeight.Bold)
            Text(state.runtime.target?.label.orEmpty())
            val session = state.runtime.session
            if (session != null) {
                Text(
                    stringResource(
                        R.string.network_interception_live_summary,
                        session.decryptedEvents,
                        session.httpRequests,
                        session.tlsErrors,
                    ),
                )
            }
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.network_interception_stop))
            }
        }
    }
}

@Composable
private fun SessionSummaryCard(session: InterceptionSession) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(session.target.label, fontWeight = FontWeight.SemiBold)
            Text(session.target.packageName, style = MaterialTheme.typography.labelSmall)
            Text(
                stringResource(
                    R.string.network_interception_session_summary,
                    session.decryptedEvents,
                    session.httpRequests,
                    session.httpResponses,
                    session.tlsErrors,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun interceptionStatusText(status: InterceptionStatus): String = stringResource(
    when (status) {
        InterceptionStatus.IDLE -> R.string.network_interception_status_idle
        InterceptionStatus.STARTING -> R.string.network_interception_status_starting
        InterceptionStatus.VERIFYING_CA -> R.string.network_interception_status_verifying_ca
        InterceptionStatus.STARTING_PROXY -> R.string.network_interception_status_starting_proxy
        InterceptionStatus.RUNNING -> R.string.network_interception_status_running
        InterceptionStatus.STOPPING -> R.string.network_interception_status_stopping
        InterceptionStatus.STOPPED -> R.string.network_interception_status_stopped
        InterceptionStatus.ADDON_DISCONNECTED -> R.string.network_interception_status_addon_disconnected
        InterceptionStatus.PLAINTEXT_STREAM_INTERRUPTED -> R.string.network_interception_status_stream_interrupted
        InterceptionStatus.ERROR -> R.string.network_interception_status_error
    },
)

@Composable
private fun certificateStatusText(state: NetworkInterceptionUiState): String = when {
    state.certificate.systemTrusted -> stringResource(R.string.network_interception_ca_trusted)
    state.certificate.systemModuleInstalled -> stringResource(R.string.network_interception_ca_staged)
    state.certificate.available -> stringResource(R.string.network_interception_ca_available)
    else -> stringResource(R.string.network_interception_ca_missing)
}

private enum class InterceptionConfirmation(val titleRes: Int, val bodyRes: Int, val actionRes: Int) {
    START(R.string.network_interception_confirm_start_title, R.string.network_interception_confirm_start_body, R.string.network_interception_start),
    INSTALL_CA(R.string.network_interception_confirm_install_title, R.string.network_interception_confirm_install_body, R.string.network_interception_install_ca),
    REMOVE_CA(R.string.network_interception_confirm_remove_title, R.string.network_interception_confirm_remove_body, R.string.network_interception_remove_ca),
}
