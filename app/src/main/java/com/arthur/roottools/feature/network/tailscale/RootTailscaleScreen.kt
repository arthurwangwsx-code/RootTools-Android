package com.arthur.roottools.feature.network.tailscale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arthur.roottools.R
import com.arthur.roottools.core.ui.component.RootToolsSectionHeader
import com.arthur.roottools.core.ui.component.RootToolsStatusChip
import com.arthur.roottools.core.ui.token.RootToolsRadius
import com.arthur.roottools.core.ui.token.RootToolsSpacing
import com.arthur.roottools.core.ui.token.RootToolsStatusTone
import com.arthur.roottools.feature.network.tailscale.data.RootTailscaleRuntimeSpec
import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleActionCode
import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleHealth
import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleMode

@Composable
fun RootTailscaleRoute(
    onBack: () -> Unit,
    viewModel: RootTailscaleViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(state.pendingAuthUrl) {
        state.pendingAuthUrl?.let { url ->
            runCatching { uriHandler.openUri(url) }
            viewModel.consumeAuthUrl()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    RootTailscaleScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onInstall = viewModel::installRuntime,
        onAuthenticate = viewModel::beginAuthentication,
        onEnableUserspaceServe = viewModel::enableUserspaceServe,
        onEnable = viewModel::enableRootOverlay,
        onDisable = viewModel::disableRootOverlay,
        onRepair = viewModel::repair,
        onBoot = viewModel::setBootEnabled,
        onStopOfficial = viewModel::stopOfficialApp,
        onDismissResult = viewModel::clearResult,
    )
}

@Composable
private fun RootTailscaleScreen(
    state: RootTailscaleUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onInstall: () -> Unit,
    onAuthenticate: () -> Unit,
    onEnableUserspaceServe: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onRepair: () -> Unit,
    onBoot: (Boolean) -> Unit,
    onStopOfficial: () -> Unit,
    onDismissResult: () -> Unit,
) {
    var bootConfirmation by remember { mutableStateOf<Boolean?>(null) }
    var disableConfirmation by remember { mutableStateOf(false) }
    var enableKernelConfirmation by remember { mutableStateOf(false) }
    var stopOfficialConfirmation by remember { mutableStateOf(false) }
    val busy = state.runningAction != null
    val canOfferManagementMode = state.snapshot.rootAvailable && state.snapshot.runtimeInstalled &&
        (state.snapshot.authenticated || state.snapshot.hasSavedIdentity) &&
        state.snapshot.backendState != "NeedsLogin"

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(RootToolsSpacing.md),
            verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.md),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.root_tailscale_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.root_tailscale_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(RootToolsSpacing.sm))
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.root_tailscale_refresh))
                        }
                    }
                }
            }

            item { RootTailscaleOverviewCard(state) }

            item {
                RootToolsSectionHeader(
                    title = stringResource(R.string.root_tailscale_serve_section),
                    subtitle = stringResource(R.string.root_tailscale_serve_section_desc),
                )
            }
            item {
                ActionCard(
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.root_tailscale_serve_title),
                    body = stringResource(R.string.root_tailscale_serve_body),
                ) {
                    if (canOfferManagementMode && !state.snapshot.userspaceServeReady) {
                        Button(onClick = onEnableUserspaceServe, enabled = !busy && state.decision.canEnableUserspaceServe) {
                            Text(stringResource(R.string.root_tailscale_serve_enable))
                        }
                    }
                    if (canOfferManagementMode && !state.snapshot.adb5555Listening) {
                        Text(
                            stringResource(R.string.root_tailscale_serve_adb_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (state.snapshot.mode == RootTailscaleMode.USERSPACE_SERVE && state.snapshot.daemonRunning) {
                        OutlinedButton(onClick = { disableConfirmation = true }, enabled = !busy) {
                            Text(stringResource(R.string.root_tailscale_disable))
                        }
                    }
                }
            }

            item {
                RootToolsSectionHeader(
                    title = stringResource(R.string.root_tailscale_overlay_section),
                    subtitle = stringResource(R.string.root_tailscale_overlay_section_desc),
                )
            }
            item {
                ActionCard(
                    icon = Icons.Rounded.Router,
                    title = stringResource(R.string.root_tailscale_overlay_title),
                    body = stringResource(R.string.root_tailscale_overlay_body),
                ) {
                    if (state.decision.canEnableRootOverlay) {
                        Button(onClick = { enableKernelConfirmation = true }, enabled = !busy) {
                            Text(stringResource(R.string.root_tailscale_enable))
                        }
                    }
                    if (state.snapshot.mode == RootTailscaleMode.KERNEL_TUN && state.snapshot.daemonRunning) {
                        OutlinedButton(onClick = { disableConfirmation = true }, enabled = !busy) {
                            Text(stringResource(R.string.root_tailscale_disable))
                        }
                    }
                    if (state.decision.canRepair) {
                        OutlinedButton(onClick = onRepair, enabled = !busy) {
                            Text(stringResource(R.string.root_tailscale_repair))
                        }
                    }
                }
            }

            if (state.decision.canBeginAuthentication) {
                item {
                    RootToolsSectionHeader(
                        title = stringResource(R.string.root_tailscale_auth_section),
                        subtitle = stringResource(R.string.root_tailscale_auth_section_desc),
                    )
                }
                item {
                    ActionCard(
                        icon = Icons.Rounded.OpenInBrowser,
                        title = stringResource(R.string.root_tailscale_auth_title),
                        body = stringResource(R.string.root_tailscale_auth_body),
                    ) {
                        Button(
                            onClick = onAuthenticate,
                            enabled = !busy,
                        ) {
                            Text(stringResource(R.string.root_tailscale_auth_action))
                        }
                    }
                }
            }

            item {
                RootToolsSectionHeader(
                    title = stringResource(R.string.root_tailscale_coexistence_section),
                    subtitle = stringResource(R.string.root_tailscale_coexistence_section_desc),
                )
            }
            item { CoexistenceCard(state, { stopOfficialConfirmation = true }, busy) }

            item {
                RootToolsSectionHeader(
                    title = stringResource(R.string.root_tailscale_runtime_section),
                    subtitle = stringResource(R.string.root_tailscale_runtime_section_desc),
                )
            }
            item {
                ActionCard(
                    icon = Icons.Rounded.SystemUpdateAlt,
                    title = stringResource(R.string.root_tailscale_runtime_title, RootTailscaleRuntimeSpec.VERSION),
                    body = stringResource(R.string.root_tailscale_runtime_body),
                ) {
                    Button(onClick = onInstall, enabled = !busy && state.snapshot.rootAvailable) {
                        Text(
                            stringResource(
                                if (state.snapshot.runtimeInstalled) R.string.root_tailscale_runtime_update else R.string.root_tailscale_runtime_install,
                            ),
                        )
                    }
                }
            }

            item {
                RootToolsSectionHeader(
                    title = stringResource(R.string.root_tailscale_boot_section),
                    subtitle = stringResource(R.string.root_tailscale_boot_section_desc),
                )
            }
            item {
                Card(
                    shape = RoundedCornerShape(RootToolsRadius.card),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(RootToolsSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.root_tailscale_boot_title), fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.root_tailscale_boot_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.snapshot.bootEnabled,
                            onCheckedChange = { bootConfirmation = it },
                            enabled = !busy && state.snapshot.rootAvailable &&
                                (state.snapshot.bootEnabled || state.decision.canEnableBoot),
                        )
                    }
                }
            }

            item {
                RootToolsSectionHeader(
                    title = stringResource(R.string.root_tailscale_diagnostics_section),
                    subtitle = stringResource(R.string.root_tailscale_diagnostics_section_desc),
                )
            }
            item { DiagnosticsCard(state) }

            item { Spacer(Modifier.height(RootToolsSpacing.lg)) }
        }
    }

    bootConfirmation?.let { enabled ->
        AlertDialog(
            onDismissRequest = { bootConfirmation = null },
            title = { Text(stringResource(if (enabled) R.string.root_tailscale_boot_confirm_title else R.string.root_tailscale_boot_disable_confirm_title)) },
            text = { Text(stringResource(if (enabled) R.string.root_tailscale_boot_confirm_body else R.string.root_tailscale_boot_disable_confirm_body)) },
            confirmButton = {
                Button(onClick = {
                    bootConfirmation = null
                    onBoot(enabled)
                }) { Text(stringResource(R.string.root_tailscale_confirm)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { bootConfirmation = null }) { Text(stringResource(R.string.root_tailscale_cancel)) }
            },
        )
    }
    if (enableKernelConfirmation) {
        AlertDialog(
            onDismissRequest = { enableKernelConfirmation = false },
            title = { Text(stringResource(R.string.root_tailscale_enable_confirm_title)) },
            text = { Text(stringResource(R.string.root_tailscale_enable_confirm_body)) },
            confirmButton = {
                Button(onClick = {
                    enableKernelConfirmation = false
                    onEnable()
                }) { Text(stringResource(R.string.root_tailscale_enable)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { enableKernelConfirmation = false }) {
                    Text(stringResource(R.string.root_tailscale_cancel))
                }
            },
        )
    }
    if (disableConfirmation) {
        AlertDialog(
            onDismissRequest = { disableConfirmation = false },
            title = { Text(stringResource(R.string.root_tailscale_disable_confirm_title)) },
            text = { Text(stringResource(R.string.root_tailscale_disable_confirm_body)) },
            confirmButton = {
                Button(onClick = {
                    disableConfirmation = false
                    onDisable()
                }) { Text(stringResource(R.string.root_tailscale_disable)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { disableConfirmation = false }) { Text(stringResource(R.string.root_tailscale_cancel)) }
            },
        )
    }
    if (stopOfficialConfirmation) {
        AlertDialog(
            onDismissRequest = { stopOfficialConfirmation = false },
            title = { Text(stringResource(R.string.root_tailscale_stop_official_confirm_title)) },
            text = { Text(stringResource(R.string.root_tailscale_stop_official_confirm_body)) },
            confirmButton = {
                Button(onClick = {
                    stopOfficialConfirmation = false
                    onStopOfficial()
                }) { Text(stringResource(R.string.root_tailscale_stop_official_app)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { stopOfficialConfirmation = false }) {
                    Text(stringResource(R.string.root_tailscale_cancel))
                }
            },
        )
    }
    state.lastResult?.let { result ->
        AlertDialog(
            onDismissRequest = onDismissResult,
            title = {
                Text(stringResource(if (result.success) R.string.root_tailscale_success else R.string.root_tailscale_failed))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.xs)) {
                    Text(stringResource(actionMessageRes(result.code)))
                    result.detail?.takeIf(String::isNotBlank)?.let {
                        Text(
                            it.take(800),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = onDismissResult) { Text(stringResource(R.string.root_tailscale_dismiss)) }
            },
        )
    }
    state.technicalError?.let { error ->
        AlertDialog(
            onDismissRequest = onDismissResult,
            title = { Text(stringResource(R.string.root_tailscale_failed)) },
            text = { Text(stringResource(R.string.root_tailscale_unexpected_error, error)) },
            confirmButton = { Button(onClick = onDismissResult) { Text(stringResource(R.string.root_tailscale_dismiss)) } },
        )
    }
}

@Composable
private fun RootTailscaleOverviewCard(state: RootTailscaleUiState) {
    val tone = when (state.decision.health) {
        RootTailscaleHealth.READY -> RootToolsStatusTone.Success
        RootTailscaleHealth.NEEDS_LOGIN, RootTailscaleHealth.DEGRADED -> RootToolsStatusTone.Warning
        RootTailscaleHealth.RUNTIME_MISSING, RootTailscaleHealth.STOPPED -> RootToolsStatusTone.Neutral
    }
    Card(
        shape = RoundedCornerShape(RootToolsRadius.dialog),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(RootToolsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.root_tailscale_overview_title),
                    modifier = Modifier.weight(1f).padding(start = RootToolsSpacing.sm),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                RootToolsStatusChip(label = stringResource(healthRes(state.decision.health)), tone = tone)
            }
            KeyValueRow(stringResource(R.string.root_tailscale_mode_label), stringResource(modeRes(state.snapshot.mode)))
            KeyValueRow(stringResource(R.string.root_tailscale_ip_label), state.snapshot.tailnetIpv4 ?: stringResource(R.string.common_dash))
            KeyValueRow(stringResource(R.string.root_tailscale_runtime_label), state.snapshot.runtimeVersion ?: stringResource(R.string.root_tailscale_not_installed))
            KeyValueRow(
                stringResource(R.string.root_tailscale_vpn_owner_label),
                state.snapshot.androidVpnOwner ?: stringResource(
                    if (state.snapshot.androidVpnActive) {
                        R.string.root_tailscale_vpn_owner_unavailable
                    } else {
                        R.string.root_tailscale_vpn_slot_free
                    },
                ),
            )
        }
    }
}

@Composable
private fun CoexistenceCard(state: RootTailscaleUiState, onStopOfficial: () -> Unit, busy: Boolean) {
    Card(
        shape = RoundedCornerShape(RootToolsRadius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(RootToolsSpacing.md),
            verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.sm),
        ) {
            Text(stringResource(R.string.root_tailscale_coexistence_title), fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    state.snapshot.hiddifyVpnActive && state.decision.coexistenceReady -> stringResource(R.string.root_tailscale_coexistence_hiddify_ready)
                    state.snapshot.officialVpnActive && state.decision.canStopOfficialApp -> stringResource(R.string.root_tailscale_coexistence_official_ready_to_release)
                    state.snapshot.officialVpnActive -> stringResource(R.string.root_tailscale_coexistence_official_blocking)
                    state.snapshot.androidVpnActive && state.decision.coexistenceReady -> stringResource(R.string.root_tailscale_coexistence_other_vpn_ready)
                    state.snapshot.androidVpnActive -> stringResource(R.string.root_tailscale_coexistence_other_vpn_waiting)
                    state.snapshot.managementReady && !state.snapshot.androidVpnActive ->
                        stringResource(R.string.root_tailscale_coexistence_slot_ready)
                    else -> stringResource(R.string.root_tailscale_coexistence_waiting)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.decision.canStopOfficialApp) {
                Button(onClick = onStopOfficial, enabled = !busy) {
                    Text(stringResource(R.string.root_tailscale_stop_official_app))
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(state: RootTailscaleUiState) {
    Card(
        shape = RoundedCornerShape(RootToolsRadius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(RootToolsSpacing.md),
            verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.xs),
        ) {
            KeyValueRow(stringResource(R.string.root_tailscale_diag_root), yesNo(state.snapshot.rootAvailable))
            KeyValueRow(stringResource(R.string.root_tailscale_diag_daemon), yesNo(state.snapshot.daemonRunning))
            KeyValueRow(stringResource(R.string.root_tailscale_diag_socket), yesNo(state.snapshot.socketReady))
            KeyValueRow(stringResource(R.string.root_tailscale_diag_identity), yesNo(state.snapshot.hasSavedIdentity))
            KeyValueRow(stringResource(R.string.root_tailscale_diag_backend), state.snapshot.backendState ?: stringResource(R.string.common_dash))
            KeyValueRow(stringResource(R.string.root_tailscale_diag_online), yesNo(state.snapshot.backendOnline))
            KeyValueRow(stringResource(R.string.root_tailscale_diag_tun), yesNo(state.snapshot.tailscale0Present))
            KeyValueRow(stringResource(R.string.root_tailscale_diag_route), yesNo(state.snapshot.routeReady))
            KeyValueRow(stringResource(R.string.root_tailscale_diag_adb), yesNo(state.snapshot.adb5555Listening))
            KeyValueRow(stringResource(R.string.root_tailscale_diag_serve_adb), yesNo(state.snapshot.serveAdbReady))
            KeyValueRow(stringResource(R.string.root_tailscale_diag_serve_mcp), yesNo(state.snapshot.serveMcpReady))
            KeyValueRow(stringResource(R.string.root_tailscale_diag_vpn_active), yesNo(state.snapshot.androidVpnActive))
            KeyValueRow(stringResource(R.string.root_tailscale_diag_boot), yesNo(state.snapshot.bootEnabled))
        }
    }
}

@Composable
private fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actions: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(RootToolsRadius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(RootToolsSpacing.md),
            verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, modifier = Modifier.padding(start = RootToolsSpacing.sm), fontWeight = FontWeight.SemiBold)
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.sm)) { actions() }
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun yesNo(value: Boolean): String = stringResource(if (value) R.string.root_tailscale_yes else R.string.root_tailscale_no)

private fun healthRes(health: RootTailscaleHealth): Int = when (health) {
    RootTailscaleHealth.RUNTIME_MISSING -> R.string.root_tailscale_health_runtime_missing
    RootTailscaleHealth.STOPPED -> R.string.root_tailscale_health_stopped
    RootTailscaleHealth.NEEDS_LOGIN -> R.string.root_tailscale_health_needs_login
    RootTailscaleHealth.READY -> R.string.root_tailscale_health_ready
    RootTailscaleHealth.DEGRADED -> R.string.root_tailscale_health_degraded
}

private fun modeRes(mode: RootTailscaleMode): Int = when (mode) {
    RootTailscaleMode.NOT_INSTALLED -> R.string.root_tailscale_mode_missing
    RootTailscaleMode.STOPPED -> R.string.root_tailscale_mode_stopped
    RootTailscaleMode.USERSPACE -> R.string.root_tailscale_mode_userspace
    RootTailscaleMode.USERSPACE_SERVE -> R.string.root_tailscale_mode_userspace_serve
    RootTailscaleMode.KERNEL_TUN -> R.string.root_tailscale_mode_kernel
}

private fun actionMessageRes(code: RootTailscaleActionCode): Int = when (code) {
    RootTailscaleActionCode.NO_ROOT -> R.string.root_tailscale_result_no_root
    RootTailscaleActionCode.RUNTIME_MISSING -> R.string.root_tailscale_result_runtime_missing
    RootTailscaleActionCode.RUNTIME_INSTALLED -> R.string.root_tailscale_result_runtime_installed
    RootTailscaleActionCode.RUNTIME_INSTALL_FAILED -> R.string.root_tailscale_result_runtime_install_failed
    RootTailscaleActionCode.AUTH_REQUIRED -> R.string.root_tailscale_result_auth_required
    RootTailscaleActionCode.AUTH_STARTED -> R.string.root_tailscale_result_auth_started
    RootTailscaleActionCode.AUTH_ALREADY_COMPLETE -> R.string.root_tailscale_result_auth_complete
    RootTailscaleActionCode.USERSPACE_SERVE_ENABLED -> R.string.root_tailscale_result_serve_enabled
    RootTailscaleActionCode.USERSPACE_SERVE_FAILED -> R.string.root_tailscale_result_serve_failed
    RootTailscaleActionCode.ENABLED -> R.string.root_tailscale_result_enabled
    RootTailscaleActionCode.ENABLE_FAILED -> R.string.root_tailscale_result_enable_failed
    RootTailscaleActionCode.DISABLED -> R.string.root_tailscale_result_disabled
    RootTailscaleActionCode.DISABLE_FAILED -> R.string.root_tailscale_result_disable_failed
    RootTailscaleActionCode.REPAIRED -> R.string.root_tailscale_result_repaired
    RootTailscaleActionCode.REPAIR_FAILED -> R.string.root_tailscale_result_repair_failed
    RootTailscaleActionCode.BOOT_ENABLED -> R.string.root_tailscale_result_boot_enabled
    RootTailscaleActionCode.BOOT_DISABLED -> R.string.root_tailscale_result_boot_disabled
    RootTailscaleActionCode.BOOT_CHANGE_FAILED -> R.string.root_tailscale_result_boot_failed
    RootTailscaleActionCode.OFFICIAL_APP_STOPPED -> R.string.root_tailscale_result_official_stopped
    RootTailscaleActionCode.OFFICIAL_APP_STOP_FAILED -> R.string.root_tailscale_result_official_stop_failed
}
