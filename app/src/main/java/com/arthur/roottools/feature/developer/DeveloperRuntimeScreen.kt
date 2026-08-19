package com.arthur.roottools.feature.developer

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arthur.roottools.R
import com.arthur.roottools.integration.termux.TermuxManagedTaskId
import com.arthur.roottools.integration.termux.TermuxRunCommandContract
import com.arthur.roottools.model.RuntimeToolState
import com.arthur.roottools.model.TermuxBridgeMode
import com.arthur.roottools.model.TermuxDistribution
import java.io.File

@Composable
fun DeveloperRuntimeRoute(
    onBack: () -> Unit,
    viewModel: DeveloperRuntimeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DeveloperRuntimeScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onProvisionCli = viewModel::provisionCli,
        onRevokeCli = viewModel::revokeCli,
        onRunReadOnlyTask = viewModel::runReadOnlyTask,
        onInstallCli = viewModel::installCliInTermux,
        onVerifyCli = viewModel::verifyCliInTermux,
        onInstallPreset = viewModel::installDeveloperPreset,
        onStartSshd = viewModel::startSshd,
        onStopSshd = viewModel::stopSshd,
        onEnableSshdAutostart = viewModel::enableSshdAutostart,
        onDisableSshdAutostart = viewModel::disableSshdAutostart,
    )
}

@Composable
private fun DeveloperRuntimeScreen(
    state: DeveloperRuntimeUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onProvisionCli: () -> Unit,
    onRevokeCli: () -> Unit,
    onRunReadOnlyTask: (TermuxManagedTaskId) -> Unit,
    onInstallCli: () -> Unit,
    onVerifyCli: () -> Unit,
    onInstallPreset: () -> Unit,
    onStartSshd: () -> Unit,
    onStopSshd: () -> Unit,
    onEnableSshdAutostart: () -> Unit,
    onDisableSshdAutostart: () -> Unit,
) {
    val context = LocalContext.current
    var confirmation by remember { mutableStateOf<DeveloperRuntimeConfirmation?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        onRefresh()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.developer_runtime_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.developer_runtime_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(10.dp))
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.developer_runtime_refresh))
                        }
                    }
                }
            }

            item { RuntimeOverviewCard(state) }

            if (!state.runtime.installed) {
                item {
                    InfoCard(
                        title = stringResource(R.string.developer_runtime_title),
                        body = stringResource(R.string.developer_runtime_not_installed),
                    )
                }
            } else {
                if (state.runtime.distribution == TermuxDistribution.GOOGLE_PLAY &&
                    !state.runtime.runCommandServiceAvailable
                ) {
                    item {
                        InfoCard(
                            title = "Google Play Termux",
                            body = stringResource(R.string.developer_runtime_play_notice),
                        )
                    }
                }

                item {
                    CliBridgeCard(
                        state = state,
                        onProvisionCli = onProvisionCli,
                        onRevokeCli = onRevokeCli,
                        onInstallCli = onInstallCli,
                        onVerifyCli = onVerifyCli,
                        onShareCli = {
                            val path = state.cliArtifactPath
                            if (path != null) {
                                shareCliFile(context, File(path))
                            }
                        },
                    )
                }

                item {
                    OfficialBridgeCard(
                        state = state,
                        onRequestPermission = {
                            permissionLauncher.launch(TermuxRunCommandContract.PERMISSION_RUN_COMMAND)
                        },
                        onRunTask = onRunReadOnlyTask,
                    )
                }

                item { DeveloperToolsCard(state) }

                item {
                    DeveloperPresetCard(
                        state = state,
                        onInstall = { confirmation = DeveloperRuntimeConfirmation.INSTALL_PRESET },
                    )
                }

                item {
                    SshdServiceCard(
                        state = state,
                        onReadConfig = { onRunReadOnlyTask(TermuxManagedTaskId.SSHD_CONFIG) },
                        onStatus = { onRunReadOnlyTask(TermuxManagedTaskId.SSHD_STATUS) },
                        onStart = { confirmation = DeveloperRuntimeConfirmation.START_SSHD },
                        onStop = onStopSshd,
                        onEnableAutostart = { confirmation = DeveloperRuntimeConfirmation.ENABLE_SSHD_AUTOSTART },
                        onDisableAutostart = { confirmation = DeveloperRuntimeConfirmation.DISABLE_SSHD_AUTOSTART },
                    )
                }

                state.lastTaskResult?.let { result ->
                    item { TaskResultCard(state) }
                }

                item { AuditCard(state) }
            }

            state.message?.let { message ->
                item { MessageCard(message, error = false) }
            }
            state.error?.let { error ->
                item { MessageCard(error, error = true) }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }

    confirmation?.let { pending ->
        val (title, body) = when (pending) {
            DeveloperRuntimeConfirmation.INSTALL_PRESET ->
                R.string.developer_runtime_bootstrap_confirm_title to R.string.developer_runtime_bootstrap_confirm_body
            DeveloperRuntimeConfirmation.START_SSHD ->
                R.string.developer_runtime_sshd_start_confirm_title to R.string.developer_runtime_sshd_start_confirm_body
            DeveloperRuntimeConfirmation.ENABLE_SSHD_AUTOSTART,
            DeveloperRuntimeConfirmation.DISABLE_SSHD_AUTOSTART ->
                R.string.developer_runtime_sshd_persistence_confirm_title to R.string.developer_runtime_sshd_persistence_confirm_body
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(stringResource(title)) },
            text = { Text(stringResource(body)) },
            confirmButton = {
                Button(onClick = {
                    confirmation = null
                    when (pending) {
                        DeveloperRuntimeConfirmation.INSTALL_PRESET -> onInstallPreset()
                        DeveloperRuntimeConfirmation.START_SSHD -> onStartSshd()
                        DeveloperRuntimeConfirmation.ENABLE_SSHD_AUTOSTART -> onEnableSshdAutostart()
                        DeveloperRuntimeConfirmation.DISABLE_SSHD_AUTOSTART -> onDisableSshdAutostart()
                    }
                }) {
                    Text(stringResource(R.string.developer_runtime_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmation = null }) {
                    Text(stringResource(R.string.developer_runtime_cancel))
                }
            },
        )
    }
}

@Composable
private fun RuntimeOverviewCard(state: DeveloperRuntimeUiState) {
    val runtime = state.runtime
    SectionCard(title = stringResource(R.string.developer_runtime_overview)) {
        RuntimeRow(
            stringResource(R.string.developer_runtime_installed),
            stringResource(if (runtime.installed) R.string.developer_runtime_yes else R.string.developer_runtime_no),
        )
        RuntimeRow(
            stringResource(R.string.developer_runtime_version),
            runtime.versionName ?: stringResource(R.string.developer_runtime_unknown),
        )
        RuntimeRow(
            stringResource(R.string.developer_runtime_distribution),
            runtime.distribution.displayName,
        )
        RuntimeRow(
            stringResource(R.string.developer_runtime_bridge),
            bridgeLabel(runtime.bridgeMode),
        )
        RuntimeRow(
            stringResource(R.string.developer_runtime_run_command_service),
            stringResource(if (runtime.runCommandServiceAvailable) R.string.developer_runtime_yes else R.string.developer_runtime_no),
        )
        RuntimeRow(
            stringResource(R.string.developer_runtime_run_command_permission),
            stringResource(if (runtime.runCommandPermissionGranted) R.string.developer_runtime_granted else R.string.developer_runtime_not_granted),
        )
    }
}

@Composable
private fun CliBridgeCard(
    state: DeveloperRuntimeUiState,
    onProvisionCli: () -> Unit,
    onRevokeCli: () -> Unit,
    onInstallCli: () -> Unit,
    onVerifyCli: () -> Unit,
    onShareCli: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.developer_runtime_cli_title)) {
        Text(
            stringResource(R.string.developer_runtime_cli_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        RuntimeRow(
            stringResource(R.string.developer_runtime_cli_active),
            state.termuxClient?.displayName ?: stringResource(R.string.developer_runtime_cli_inactive),
        )
        if (state.cliArtifactPath != null) {
            state.cliArtifactVersion?.let {
                Text(
                    stringResource(R.string.developer_runtime_cli_version, it),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                stringResource(R.string.developer_runtime_cli_share_warning),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                stringResource(R.string.developer_runtime_cli_install_hint),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.cliVerification?.let { verification ->
            Text(
                stringResource(
                    if (verification.matchesGeneratedArtifact) {
                        R.string.developer_runtime_cli_checksum_match
                    } else {
                        R.string.developer_runtime_cli_checksum_mismatch
                    }
                ),
                modifier = Modifier.padding(top = 8.dp),
                color = if (verification.matchesGeneratedArtifact) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onProvisionCli, enabled = !state.loading) {
                Icon(Icons.Rounded.VerifiedUser, null)
                Text(stringResource(R.string.developer_runtime_cli_generate), Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = onShareCli, enabled = state.cliArtifactPath != null) {
                Icon(Icons.Rounded.Share, null)
                Text(stringResource(R.string.developer_runtime_cli_share), Modifier.padding(start = 6.dp))
            }
        }
        if (state.termuxClient != null) {
            if (state.runtime.bridgeMode == TermuxBridgeMode.OFFICIAL_RUN_COMMAND && state.cliArtifactPath != null) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onInstallCli, enabled = state.runningTask == null) {
                        Text(stringResource(R.string.developer_runtime_cli_install))
                    }
                    OutlinedButton(onClick = onVerifyCli, enabled = state.runningTask == null) {
                        Text(stringResource(R.string.developer_runtime_cli_verify))
                    }
                }
            }
            OutlinedButton(onClick = onRevokeCli, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.developer_runtime_cli_revoke))
            }
        }
    }
}

@Composable
private fun OfficialBridgeCard(
    state: DeveloperRuntimeUiState,
    onRequestPermission: () -> Unit,
    onRunTask: (TermuxManagedTaskId) -> Unit,
) {
    SectionCard(title = stringResource(R.string.developer_runtime_official_title)) {
        Text(
            stringResource(R.string.developer_runtime_official_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        when (state.runtime.bridgeMode) {
            TermuxBridgeMode.OFFICIAL_RUN_COMMAND_PERMISSION_REQUIRED -> {
                Text(
                    stringResource(R.string.developer_runtime_allow_external_apps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onRequestPermission, modifier = Modifier.padding(top = 10.dp)) {
                    Text(stringResource(R.string.developer_runtime_request_permission))
                }
            }

            TermuxBridgeMode.OFFICIAL_RUN_COMMAND -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ManagedTaskButton(
                        label = stringResource(R.string.developer_runtime_task_info),
                        taskId = TermuxManagedTaskId.TERMUX_INFO,
                        state = state,
                        onRunTask = onRunTask,
                        modifier = Modifier.weight(1f),
                    )
                    ManagedTaskButton(
                        label = stringResource(R.string.developer_runtime_task_probe),
                        taskId = TermuxManagedTaskId.RUNTIME_PROBE,
                        state = state,
                        onRunTask = onRunTask,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedButton(
                    onClick = { onRunTask(TermuxManagedTaskId.PACKAGE_INVENTORY) },
                    enabled = state.runningTask == null,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.developer_runtime_task_packages))
                }
            }

            else -> Text(
                bridgeLabel(state.runtime.bridgeMode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ManagedTaskButton(
    label: String,
    taskId: TermuxManagedTaskId,
    state: DeveloperRuntimeUiState,
    onRunTask: (TermuxManagedTaskId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = { onRunTask(taskId) },
        enabled = state.runningTask == null,
        modifier = modifier,
    ) {
        if (state.runningTask == taskId) {
            Text(stringResource(R.string.developer_runtime_task_running))
        } else {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DeveloperToolsCard(state: DeveloperRuntimeUiState) {
    SectionCard(title = stringResource(R.string.developer_runtime_tools_title)) {
        ToolStateRow(stringResource(R.string.developer_runtime_tool_git), state.runtime.git)
        ToolStateRow(stringResource(R.string.developer_runtime_tool_python), state.runtime.python)
        ToolStateRow(stringResource(R.string.developer_runtime_tool_node), state.runtime.node)
        ToolStateRow(stringResource(R.string.developer_runtime_tool_sshd), state.runtime.sshd)
    }
}

@Composable
private fun DeveloperPresetCard(
    state: DeveloperRuntimeUiState,
    onInstall: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.developer_runtime_bootstrap_title)) {
        Text(
            stringResource(R.string.developer_runtime_bootstrap_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onInstall,
            enabled = state.runtime.bridgeMode == TermuxBridgeMode.OFFICIAL_RUN_COMMAND && state.runningTask == null,
            modifier = Modifier.padding(top = 10.dp),
        ) {
            Text(stringResource(R.string.developer_runtime_bootstrap_install))
        }
    }
}

@Composable
private fun SshdServiceCard(
    state: DeveloperRuntimeUiState,
    onReadConfig: () -> Unit,
    onStatus: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onEnableAutostart: () -> Unit,
    onDisableAutostart: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.developer_runtime_sshd_title)) {
        Text(
            stringResource(R.string.developer_runtime_sshd_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.developer_runtime_services_restart_hint),
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.sshdConfig?.let { config ->
            Spacer(Modifier.height(8.dp))
            RuntimeRow(
                stringResource(R.string.developer_runtime_sshd_port),
                config.port?.toString() ?: stringResource(R.string.developer_runtime_unknown),
            )
            RuntimeRow(
                stringResource(R.string.developer_runtime_sshd_listen),
                config.listenAddresses.joinToString().ifBlank { stringResource(R.string.developer_runtime_unknown) },
            )
            RuntimeRow(
                stringResource(R.string.developer_runtime_sshd_password_auth),
                config.passwordAuthentication?.let { yesNo(it) } ?: stringResource(R.string.developer_runtime_unknown),
            )
            RuntimeRow(
                stringResource(R.string.developer_runtime_sshd_pubkey_auth),
                config.publicKeyAuthentication?.let { yesNo(it) } ?: stringResource(R.string.developer_runtime_unknown),
            )
            if (config.wildcardListenerConfigured) {
                Text(
                    stringResource(R.string.developer_runtime_sshd_wildcard_warning),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        val enabled = state.runtime.bridgeMode == TermuxBridgeMode.OFFICIAL_RUN_COMMAND && state.runningTask == null
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onReadConfig, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.developer_runtime_sshd_read_config), maxLines = 1)
            }
            OutlinedButton(onClick = onStatus, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.developer_runtime_sshd_status), maxLines = 1)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onStart, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.developer_runtime_sshd_start))
            }
            OutlinedButton(onClick = onStop, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.developer_runtime_sshd_stop))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onEnableAutostart, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.developer_runtime_sshd_enable_autostart), maxLines = 1)
            }
            OutlinedButton(onClick = onDisableAutostart, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.developer_runtime_sshd_disable_autostart), maxLines = 1)
            }
        }
    }
}

@Composable
private fun AuditCard(state: DeveloperRuntimeUiState) {
    SectionCard(title = stringResource(R.string.developer_runtime_audit_title)) {
        Text(
            stringResource(R.string.developer_runtime_audit_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.audit.isEmpty()) {
            Text(
                stringResource(R.string.developer_runtime_audit_empty),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            state.audit.take(8).forEach { record ->
                Text(
                    stringResource(
                        R.string.developer_runtime_audit_row,
                        record.taskId.name,
                        if (record.success) "OK" else "FAIL",
                        record.exitCode,
                        record.durationMs,
                    ),
                    modifier = Modifier.padding(top = 7.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun TaskResultCard(state: DeveloperRuntimeUiState) {
    val result = state.lastTaskResult ?: return
    SectionCard(title = stringResource(R.string.developer_runtime_last_result)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Terminal, contentDescription = null)
            Text(
                result.taskId.name,
                Modifier.padding(start = 8.dp).weight(1f),
                fontWeight = FontWeight.SemiBold,
            )
            Text(stringResource(R.string.developer_runtime_exit_code, result.exitCode))
        }
        val output = result.stdout.ifBlank { result.stderr }.ifBlank { result.transportError.orEmpty() }
        if (output.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    output.take(12_000),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        if (result.truncatedByTermux) {
            Text(
                stringResource(R.string.developer_runtime_output_truncated),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun RuntimeRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ToolStateRow(label: String, state: RuntimeToolState) {
    val value = when (state) {
        RuntimeToolState.INSTALLED -> stringResource(R.string.developer_runtime_tool_installed)
        RuntimeToolState.NOT_INSTALLED -> stringResource(R.string.developer_runtime_tool_missing)
        RuntimeToolState.UNKNOWN -> stringResource(R.string.developer_runtime_tool_unknown)
    }
    RuntimeRow(label, value)
}

@Composable
private fun InfoCard(title: String, body: String) {
    SectionCard(title) {
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageCard(message: String, error: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun bridgeLabel(mode: TermuxBridgeMode): String = when (mode) {
    TermuxBridgeMode.OFFICIAL_RUN_COMMAND -> stringResource(R.string.developer_runtime_official_bridge)
    TermuxBridgeMode.OFFICIAL_RUN_COMMAND_PERMISSION_REQUIRED -> stringResource(R.string.developer_runtime_permission_required)
    TermuxBridgeMode.REVERSE_INTENT_ONLY -> stringResource(R.string.developer_runtime_reverse_bridge)
    TermuxBridgeMode.LOCAL_SSH -> stringResource(R.string.developer_runtime_local_ssh)
    TermuxBridgeMode.UNAVAILABLE -> stringResource(R.string.developer_runtime_unavailable)
}

@Composable
private fun yesNo(value: Boolean): String = stringResource(
    if (value) R.string.developer_runtime_yes else R.string.developer_runtime_no,
)

private enum class DeveloperRuntimeConfirmation {
    INSTALL_PRESET,
    START_SSHD,
    ENABLE_SSHD_AUTOSTART,
    DISABLE_SSHD_AUTOSTART,
}

private fun shareCliFile(context: android.content.Context, file: File) {
    if (!file.isFile) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.developer_runtime_share_chooser)))
}

