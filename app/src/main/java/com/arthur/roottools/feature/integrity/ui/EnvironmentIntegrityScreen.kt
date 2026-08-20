package com.arthur.roottools.feature.integrity.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arthur.roottools.R
import com.arthur.roottools.feature.integrity.model.AttestationRecord
import com.arthur.roottools.feature.integrity.model.IntegrityDisposition
import com.arthur.roottools.feature.integrity.model.IntegrityFinding
import com.arthur.roottools.feature.integrity.model.IntegrityFindingCode
import com.arthur.roottools.feature.integrity.model.IntegrityReportFormat
import com.arthur.roottools.feature.integrity.model.IntegrityScanMode
import com.arthur.roottools.feature.integrity.model.IntegritySnapshot
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun EnvironmentIntegrityRoute(onBack: () -> Unit) {
    val viewModel: IntegrityViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.ensureFastScan() }
    EnvironmentIntegrityScreen(
        state = state,
        onBack = onBack,
        onScan = viewModel::scan,
        onSaveBaseline = viewModel::saveCurrentBaseline,
        onClearBaseline = viewModel::clearBaseline,
        onExport = viewModel::exportReport,
        onClearMessage = viewModel::clearMessage,
    )
}

@Composable
private fun EnvironmentIntegrityScreen(
    state: IntegrityUiState,
    onBack: () -> Unit,
    onScan: (IntegrityScanMode) -> Unit,
    onSaveBaseline: (String) -> Unit,
    onClearBaseline: () -> Unit,
    onExport: (IntegrityReportFormat) -> Unit,
    onClearMessage: () -> Unit,
) {
    val context = LocalContext.current
    val defaultBaselineProfile = stringResource(R.string.integrity_default_baseline_profile, Build.MODEL)
    var showTrustDialog by remember { mutableStateOf(false) }
    val snapshot = state.snapshot
    val hasScan = snapshot.scannedAtEpochMs > 0L

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { IntegrityHeader(state.loading, onBack) { onScan(IntegrityScanMode.FAST) } }
            item { ScanModeCard(state.scanningMode, onScan) }

            if (!hasScan && state.loading) {
                item { LoadingCard(state.scanningMode ?: IntegrityScanMode.FAST) }
            }

            if (hasScan) {
                item { IntegritySummaryCard(snapshot) }
                item {
                    TrustedBaselineCard(
                        state = state,
                        onTrust = { showTrustDialog = true },
                        onClear = onClearBaseline,
                    )
                }
                item { SectionTitle(R.string.integrity_findings_title, R.string.integrity_findings_subtitle) }
                if (snapshot.findings.isEmpty()) {
                    item { InfoSurface(stringResource(R.string.integrity_no_findings)) }
                } else {
                    items(snapshot.findings, key = { "${it.code}-${it.observed}-${it.disposition}" }) { finding ->
                        FindingCard(finding)
                    }
                }

                item { SectionTitle(R.string.integrity_runtime_title, R.string.integrity_runtime_subtitle) }
                item { RuntimeCard(snapshot) }
                item { SectionTitle(R.string.integrity_boot_title, R.string.integrity_boot_subtitle) }
                item { BootCard(snapshot) }
                item { SectionTitle(R.string.integrity_root_title, R.string.integrity_root_subtitle) }
                item { RootRuntimeCard(snapshot) }
                item { SectionTitle(R.string.integrity_environment_title, R.string.integrity_environment_subtitle) }
                item { EnvironmentCard(snapshot) }
                item { SectionTitle(R.string.integrity_surface_title, R.string.integrity_surface_subtitle) }
                item { DeviceSurfaceCard(snapshot) }
                item { SectionTitle(R.string.integrity_attestation_title, R.string.integrity_attestation_subtitle) }
                item { AttestationSection(snapshot) }
                item { SectionTitle(R.string.integrity_reports_title, R.string.integrity_reports_subtitle) }
                item {
                    ReportCard(
                        state = state,
                        onExport = onExport,
                        onShare = { path -> shareEvidence(context, path) },
                    )
                }
                if (snapshot.unavailableProbes.isNotEmpty()) {
                    item {
                        InfoSurface(
                            stringResource(
                                R.string.integrity_unavailable_probes,
                                snapshot.unavailableProbes.sorted().joinToString(", "),
                            )
                        )
                    }
                }
            }

            state.action?.let { action ->
                item {
                    ActionMessage(
                        text = stringResource(actionMessageRes(action)),
                        error = false,
                        onDismiss = onClearMessage,
                    )
                }
            }
            state.errorDetail?.let { detail ->
                item {
                    ActionMessage(
                        text = "${stringResource(R.string.integrity_error_scan)} $detail",
                        error = true,
                        onDismiss = onClearMessage,
                    )
                }
            }
        }
    }

    if (showTrustDialog) {
        AlertDialog(
            onDismissRequest = { showTrustDialog = false },
            title = { Text(stringResource(R.string.integrity_baseline_confirm_title)) },
            text = { Text(stringResource(R.string.integrity_baseline_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showTrustDialog = false
                        onSaveBaseline(defaultBaselineProfile)
                    }
                ) {
                    Text(stringResource(R.string.integrity_baseline_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrustDialog = false }) {
                    Text(stringResource(R.string.integrity_baseline_cancel))
                }
            },
        )
    }
}

@Composable
private fun IntegrityHeader(loading: Boolean, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.integrity_back))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.integrity_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.integrity_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRefresh, enabled = !loading) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.integrity_refresh))
        }
    }
}

@Composable
private fun ScanModeCard(scanning: IntegrityScanMode?, onScan: (IntegrityScanMode) -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            IntegrityScanMode.entries.forEach { mode ->
                FilterChip(
                    modifier = Modifier.weight(1f),
                    selected = scanning == mode,
                    enabled = scanning == null,
                    onClick = { onScan(mode) },
                    label = {
                        Text(
                            stringResource(scanModeLabelRes(mode)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun LoadingCard(mode: IntegrityScanMode) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(25.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(R.string.integrity_scanning, stringResource(scanModeLabelRes(mode))),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun IntegritySummaryCard(snapshot: IntegritySnapshot) {
    val disposition = when {
        snapshot.criticalCount > 0 -> IntegrityDisposition.CRITICAL
        snapshot.warningCount > 0 -> IntegrityDisposition.WARN
        snapshot.unavailableProbes.isNotEmpty() -> IntegrityDisposition.UNAVAILABLE
        else -> IntegrityDisposition.PASS
    }
    val title = when (disposition) {
        IntegrityDisposition.CRITICAL -> stringResource(R.string.integrity_summary_critical)
        IntegrityDisposition.WARN -> stringResource(R.string.integrity_summary_warning)
        IntegrityDisposition.UNAVAILABLE -> stringResource(R.string.integrity_summary_unavailable)
        else -> stringResource(R.string.integrity_summary_clean)
    }
    val tint = dispositionTint(disposition)
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.09f)),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(48.dp), shape = RoundedCornerShape(16.dp), color = tint.copy(alpha = 0.14f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (disposition == IntegrityDisposition.PASS) Icons.Rounded.CheckCircle else Icons.Rounded.Security,
                            contentDescription = null,
                            tint = tint,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(
                            R.string.integrity_summary_meta,
                            stringResource(scanModeLabelRes(snapshot.mode)),
                            snapshot.durationMs,
                            snapshot.baselineDriftCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DispositionBadge(disposition)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetric(stringResource(R.string.integrity_metric_critical), snapshot.criticalCount, Modifier.weight(1f))
                SummaryMetric(stringResource(R.string.integrity_metric_warning), snapshot.warningCount, Modifier.weight(1f))
                SummaryMetric(stringResource(R.string.integrity_metric_expected), snapshot.expectedCount, Modifier.weight(1f))
                SummaryMetric(
                    stringResource(R.string.integrity_metric_unavailable),
                    snapshot.findings.count { it.disposition == IntegrityDisposition.UNAVAILABLE } + snapshot.unavailableProbes.size,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TrustedBaselineCard(state: IntegrityUiState, onTrust: () -> Unit, onClear: () -> Unit) {
    val baseline = state.baseline
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Save, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Text(stringResource(R.string.integrity_baseline_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                baseline?.let {
                    stringResource(
                        R.string.integrity_baseline_active,
                        it.profileName,
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.trustedAtEpochMs)),
                    )
                } ?: stringResource(R.string.integrity_baseline_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(onClick = onTrust, enabled = state.snapshot.scannedAtEpochMs > 0L && !state.loading) {
                    Text(stringResource(if (baseline == null) R.string.integrity_baseline_save else R.string.integrity_baseline_replace))
                }
                if (baseline != null) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.integrity_baseline_clear)) }
                }
            }
        }
    }
}

@Composable
private fun FindingCard(finding: IntegrityFinding) {
    val tint = dispositionTint(finding.disposition)
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Surface(Modifier.size(38.dp), shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = 0.12f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when (finding.disposition) {
                            IntegrityDisposition.PASS, IntegrityDisposition.EXPECTED -> Icons.Rounded.CheckCircle
                            IntegrityDisposition.CRITICAL, IntegrityDisposition.WARN -> Icons.Rounded.Warning
                            else -> Icons.Rounded.Security
                        },
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = tint,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(findingTitleRes(finding.code)),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    DispositionBadge(finding.disposition)
                }
                Text(
                    finding.code.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                finding.observed?.takeIf(String::isNotBlank)?.let {
                    Text(stringResource(R.string.integrity_observed, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                finding.expected?.takeIf(String::isNotBlank)?.let {
                    Text(stringResource(R.string.integrity_expected, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    stringResource(
                        R.string.integrity_confidence_sources,
                        finding.confidence.name,
                        finding.sources.joinToString(", ") { it.name },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                finding.evidence.take(4).forEach { evidence ->
                    Text(evidence, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RuntimeCard(snapshot: IntegritySnapshot) {
    val runtime = snapshot.signals.runtime
    val native = snapshot.signals.native
    DetailCard(icon = Icons.Rounded.Memory) {
        KeyValueRow(R.string.integrity_label_tracer_pid, runtime.tracerPid.toString())
        KeyValueRow(R.string.integrity_label_maps, "${runtime.mappingCount} / root ${runtime.rootMappingCount ?: "—"}")
        KeyValueRow(R.string.integrity_label_rwx, runtime.writableExecutableMappings.size.toString())
        KeyValueRow(R.string.integrity_label_deleted_exec, runtime.deletedExecutableMappings.size.toString())
        KeyValueRow(R.string.integrity_label_markers, runtime.strongMarkers.sorted().joinToString(", ").ifBlank { stringResource(R.string.integrity_value_none) })
        if (native != null) {
            if (native.available) {
                KeyValueRow(
                    R.string.integrity_label_native_self,
                    stringResource(R.string.integrity_native_self_value, native.selfExecutableSegments, native.selfExecutableSegmentMismatches),
                )
                KeyValueRow(R.string.integrity_label_markers, native.strongMarkers.sorted().joinToString(", ").ifBlank { stringResource(R.string.integrity_value_none) })
            } else {
                Text(
                    stringResource(R.string.integrity_native_unavailable_detail, native.error.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BootCard(snapshot: IntegritySnapshot) {
    val self = snapshot.signals.self
    val boot = snapshot.signals.boot
    DetailCard(icon = Icons.Rounded.Security) {
        KeyValueRow(R.string.integrity_label_package, self.packageName)
        KeyValueRow(R.string.integrity_label_version, "${self.versionName} (${self.versionCode})")
        KeyValueRow(R.string.integrity_label_signing, shortHash(self.signingSha256))
        KeyValueRow(R.string.integrity_label_apk, shortHash(self.apkSha256))
        KeyValueRow(R.string.integrity_label_debuggable, yesNo(self.debuggable))
        HorizontalDivider()
        KeyValueRow(R.string.integrity_label_model, "${boot.frameworkModel} / ${boot.rootModel.ifBlank { "—" }}")
        KeyValueRow(R.string.integrity_label_verified_boot, boot.verifiedBootState.ifBlank { stringResource(R.string.integrity_value_unknown) })
        KeyValueRow(R.string.integrity_label_vbmeta, boot.vbmetaDeviceState.ifBlank { stringResource(R.string.integrity_value_unknown) })
        KeyValueRow(R.string.integrity_label_flash_locked, nullableYesNo(boot.flashLocked))
        KeyValueRow(R.string.integrity_label_security_patch, boot.securityPatch.ifBlank { stringResource(R.string.integrity_value_unknown) })
        KeyValueRow(R.string.integrity_label_selinux, boot.selinuxMode.ifBlank { stringResource(R.string.integrity_value_unknown) })
    }
}

@Composable
private fun RootRuntimeCard(snapshot: IntegritySnapshot) {
    val root = snapshot.signals.rootRuntime
    DetailCard(icon = Icons.Rounded.Tune) {
        KeyValueRow(R.string.integrity_label_root_provider, root.provider.name)
        KeyValueRow(R.string.integrity_label_zygisk, nullableYesNo(root.zygiskEnabled))
        KeyValueRow(R.string.integrity_label_modules, root.modules.sorted().joinToString("\n").ifBlank { stringResource(R.string.integrity_value_none) })
        KeyValueRow(R.string.integrity_label_hook_frameworks, root.hookFrameworkPackages.sorted().joinToString("\n").ifBlank { stringResource(R.string.integrity_value_none) })
    }
}

@Composable
private fun EnvironmentCard(snapshot: IntegritySnapshot) {
    val environment = snapshot.signals.environment
    DetailCard(icon = Icons.Rounded.CloudDone) {
        KeyValueRow(R.string.integrity_label_developer_options, yesNo(environment.developerOptionsEnabled))
        KeyValueRow(R.string.integrity_label_adb, yesNo(environment.adbEnabled))
        KeyValueRow(R.string.integrity_label_wireless_adb, yesNo(environment.wirelessAdbEnabled))
        KeyValueRow(R.string.integrity_label_vpn, yesNo(environment.vpnActive))
        KeyValueRow(R.string.integrity_label_proxy, environment.globalProxy ?: stringResource(R.string.integrity_value_none))
        KeyValueRow(R.string.integrity_label_mock_location, environment.mockLocationPackages.sorted().joinToString(", ").ifBlank { stringResource(R.string.integrity_value_none) })
        KeyValueRow(
            R.string.integrity_label_environment_packages,
            environment.packages.joinToString("\n") { "${it.category.name} · ${it.packageName}" }.ifBlank { stringResource(R.string.integrity_value_none) },
        )
    }
}

@Composable
private fun DeviceSurfaceCard(snapshot: IntegritySnapshot) {
    val surface = snapshot.signals.deviceSurface
    val sandbox = snapshot.signals.sandbox
    if (surface == null) {
        InfoSurface(stringResource(R.string.integrity_surface_not_scanned))
        return
    }
    DetailCard(icon = Icons.Rounded.Fingerprint) {
        KeyValueRow(R.string.integrity_label_cpu_hash, shortHash(surface.cpuTopologyHash.orEmpty()))
        KeyValueRow(R.string.integrity_label_thermal_hash, shortHash(surface.thermalZoneHash.orEmpty()))
        KeyValueRow(R.string.integrity_label_gpu_hash, shortHash(surface.gpuRendererHash.orEmpty()))
        KeyValueRow(R.string.integrity_label_vulkan_hash, shortHash(surface.vulkanCapabilityHash.orEmpty()))
        KeyValueRow(R.string.integrity_label_sensor_hash, shortHash(surface.sensorShapeHash.orEmpty()))
        KeyValueRow(R.string.integrity_label_audio_hash, shortHash(surface.audioCapabilityHash.orEmpty()))
        KeyValueRow(R.string.integrity_label_battery_hash, shortHash(surface.batterySupplyHash.orEmpty()))
        Text(
            stringResource(R.string.integrity_surface_counts, surface.sensorCount, surface.audioDeviceCount, surface.thermalZoneCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        sandbox?.let {
            KeyValueRow(
                R.string.integrity_label_sandbox,
                stringResource(if (it.coherent) R.string.integrity_status_coherent else R.string.integrity_status_incoherent),
            )
        }
    }
}

@Composable
private fun AttestationSection(snapshot: IntegritySnapshot) {
    val attestation = snapshot.signals.attestation
    if (attestation == null) {
        InfoSurface(stringResource(R.string.integrity_attestation_not_scanned))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        attestation.standard?.let { AttestationCard(it, strongBox = false) }
        attestation.strongBox?.let { AttestationCard(it, strongBox = true) }
    }
}

@Composable
private fun AttestationCard(record: AttestationRecord, strongBox: Boolean) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (strongBox) Icons.Rounded.Fingerprint else Icons.Rounded.Security, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Text(if (strongBox) "StrongBox" else "Android Key Attestation", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (record.available) record.securityLevel.name else stringResource(R.string.integrity_level_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!record.available) {
                Text(record.error ?: stringResource(R.string.integrity_value_unknown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            KeyValueRow(R.string.integrity_label_attestation_security, record.securityLevel.name)
            KeyValueRow(R.string.integrity_label_keymint_security, record.keyMintSecurityLevel.name)
            KeyValueRow(R.string.integrity_label_challenge, stringResource(if (record.challengeMatched) R.string.integrity_status_match else R.string.integrity_status_mismatch))
            KeyValueRow(
                R.string.integrity_label_chain,
                stringResource(
                    R.string.integrity_chain_value,
                    record.chainLength,
                    if (record.chainSignatureValid) "OK" else "FAIL",
                    if (record.chainValidityValid) "OK" else "FAIL",
                ),
            )
            KeyValueRow(R.string.integrity_label_trust_anchor, record.trustAnchor.name)
            KeyValueRow(
                R.string.integrity_label_revocation,
                stringResource(if (record.revocationCheckedOnline) R.string.integrity_status_online_checked else R.string.integrity_status_offline),
            )
            KeyValueRow(R.string.integrity_label_rkp, yesNo(record.rkpProvisioningPresent))
            KeyValueRow(R.string.integrity_label_attested_locked, nullableYesNo(record.deviceLocked))
            KeyValueRow(R.string.integrity_label_verified_boot, record.verifiedBootState ?: stringResource(R.string.integrity_value_unknown))
        }
    }
}

@Composable
private fun ReportCard(
    state: IntegrityUiState,
    onExport: (IntegrityReportFormat) -> Unit,
    onShare: (String) -> Unit,
) {
    val lastPath = state.pemPath ?: state.reportPath
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onExport(IntegrityReportFormat.TEXT) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Save, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.integrity_export_text), maxLines = 1)
                }
                OutlinedButton(onClick = { onExport(IntegrityReportFormat.JSON) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.DataObject, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.integrity_export_json), maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onExport(IntegrityReportFormat.PEM) },
                    enabled = state.snapshot.signals.attestation != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.integrity_export_pem), maxLines = 1)
                }
                Button(onClick = { lastPath?.let(onShare) }, enabled = lastPath != null, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Share, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.integrity_share_last), maxLines = 1)
                }
            }
            lastPath?.let {
                Text(it.substringAfterLast('/'), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailCard(icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int, @StringRes subtitle: Int) {
    Column(Modifier.padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KeyValueRow(@StringRes label: Int, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(stringResource(label), modifier = Modifier.weight(0.42f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, modifier = Modifier.weight(0.58f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SummaryMetric(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun DispositionBadge(disposition: IntegrityDisposition) {
    val tint = dispositionTint(disposition)
    Surface(shape = RoundedCornerShape(50), color = tint.copy(alpha = 0.12f)) {
        Text(
            stringResource(dispositionLabelRes(disposition)),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
    }
}

@Composable
private fun InfoSurface(text: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
        Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionMessage(text: String, error: Boolean, onDismiss: () -> Unit) {
    val tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(color = tint.copy(alpha = 0.10f), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = tint)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.integrity_dismiss_symbol), color = tint) }
        }
    }
}

@Composable
private fun dispositionTint(disposition: IntegrityDisposition): Color = when (disposition) {
    IntegrityDisposition.PASS -> MaterialTheme.colorScheme.primary
    IntegrityDisposition.EXPECTED -> MaterialTheme.colorScheme.secondary
    IntegrityDisposition.INFO, IntegrityDisposition.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
    IntegrityDisposition.WARN -> MaterialTheme.colorScheme.tertiary
    IntegrityDisposition.CRITICAL -> MaterialTheme.colorScheme.error
}

@StringRes
private fun scanModeLabelRes(mode: IntegrityScanMode): Int = when (mode) {
    IntegrityScanMode.FAST -> R.string.integrity_scan_fast
    IntegrityScanMode.DEEP -> R.string.integrity_scan_deep
    IntegrityScanMode.NATIVE -> R.string.integrity_scan_native
    IntegrityScanMode.ATTESTATION -> R.string.integrity_scan_attestation
}

@StringRes
private fun dispositionLabelRes(disposition: IntegrityDisposition): Int = when (disposition) {
    IntegrityDisposition.PASS -> R.string.integrity_level_pass
    IntegrityDisposition.INFO -> R.string.integrity_level_info
    IntegrityDisposition.EXPECTED -> R.string.integrity_level_expected
    IntegrityDisposition.WARN -> R.string.integrity_level_warning
    IntegrityDisposition.CRITICAL -> R.string.integrity_level_critical
    IntegrityDisposition.UNAVAILABLE -> R.string.integrity_level_unavailable
}

@StringRes
private fun actionMessageRes(action: IntegrityUiAction): Int = when (action) {
    IntegrityUiAction.BASELINE_SAVED -> R.string.integrity_action_baseline_saved
    IntegrityUiAction.BASELINE_CLEARED -> R.string.integrity_action_baseline_cleared
    IntegrityUiAction.REPORT_EXPORTED -> R.string.integrity_action_report_exported
    IntegrityUiAction.PEM_EXPORTED -> R.string.integrity_action_pem_exported
}

@StringRes
private fun findingTitleRes(code: IntegrityFindingCode): Int = when (code) {
    IntegrityFindingCode.NO_TRUSTED_BASELINE -> R.string.integrity_finding_no_baseline
    IntegrityFindingCode.SELF_IDENTITY_OK -> R.string.integrity_finding_self_ok
    IntegrityFindingCode.SELF_COMPILED_IDENTITY_MISMATCH -> R.string.integrity_finding_self_identity_mismatch
    IntegrityFindingCode.SELF_SIGNING_BASELINE_MISMATCH -> R.string.integrity_finding_signing_mismatch
    IntegrityFindingCode.SELF_APK_BASELINE_MISMATCH -> R.string.integrity_finding_apk_mismatch
    IntegrityFindingCode.SELF_VERSION_CHANGED -> R.string.integrity_finding_version_changed
    IntegrityFindingCode.SELF_PACKAGE_PATH_MISMATCH -> R.string.integrity_finding_path_mismatch
    IntegrityFindingCode.DEBUGGABLE_BUILD -> R.string.integrity_finding_debuggable
    IntegrityFindingCode.BOOTLOADER_UNLOCKED -> R.string.integrity_finding_bootloader_unlocked
    IntegrityFindingCode.VERIFIED_BOOT_STATE_CHANGED -> R.string.integrity_finding_verified_boot_changed
    IntegrityFindingCode.SELINUX_NOT_ENFORCING -> R.string.integrity_finding_selinux
    IntegrityFindingCode.SECURITY_PATCH_CHANGED -> R.string.integrity_finding_patch_changed
    IntegrityFindingCode.ROOT_AVAILABLE -> R.string.integrity_finding_root
    IntegrityFindingCode.ROOT_PROVIDER_CHANGED -> R.string.integrity_finding_root_provider
    IntegrityFindingCode.ROOT_MODULE_SET_CHANGED -> R.string.integrity_finding_root_modules
    IntegrityFindingCode.HOOK_FRAMEWORK_PRESENT -> R.string.integrity_finding_hook_framework
    IntegrityFindingCode.RUNTIME_TRACED -> R.string.integrity_finding_traced
    IntegrityFindingCode.RUNTIME_STRONG_MARKER -> R.string.integrity_finding_runtime_marker
    IntegrityFindingCode.RUNTIME_WRITABLE_EXECUTABLE -> R.string.integrity_finding_rwx
    IntegrityFindingCode.RUNTIME_DELETED_EXECUTABLE -> R.string.integrity_finding_deleted_exec
    IntegrityFindingCode.RUNTIME_MAPS_CROSSCHECK_MISMATCH -> R.string.integrity_finding_maps_mismatch
    IntegrityFindingCode.DEVELOPER_OPTIONS_ENABLED -> R.string.integrity_finding_dev_options
    IntegrityFindingCode.ADB_ENABLED -> R.string.integrity_finding_adb
    IntegrityFindingCode.VPN_ACTIVE -> R.string.integrity_finding_vpn
    IntegrityFindingCode.GLOBAL_PROXY_CONFIGURED -> R.string.integrity_finding_proxy
    IntegrityFindingCode.AUTOMATION_TOOL_PRESENT -> R.string.integrity_finding_automation
    IntegrityFindingCode.VIRTUALIZATION_TOOL_PRESENT -> R.string.integrity_finding_virtualization
    IntegrityFindingCode.DEVICE_SPOOFING_TOOL_PRESENT -> R.string.integrity_finding_spoofing
    IntegrityFindingCode.ROOT_HIDING_TOOL_PRESENT -> R.string.integrity_finding_root_hiding
    IntegrityFindingCode.DEVICE_MODEL_CROSSCHECK_MISMATCH -> R.string.integrity_finding_model_mismatch
    IntegrityFindingCode.DEVICE_SURFACE_DRIFT -> R.string.integrity_finding_surface_drift
    IntegrityFindingCode.SANDBOX_INCOHERENT -> R.string.integrity_finding_sandbox
    IntegrityFindingCode.NATIVE_PROBE_UNAVAILABLE -> R.string.integrity_finding_native_unavailable
    IntegrityFindingCode.NATIVE_RUNTIME_FILE_MISMATCH -> R.string.integrity_finding_native_mismatch
    IntegrityFindingCode.NATIVE_STRONG_MARKER -> R.string.integrity_finding_native_marker
    IntegrityFindingCode.ATTESTATION_UNAVAILABLE -> R.string.integrity_finding_attestation_unavailable
    IntegrityFindingCode.ATTESTATION_CHALLENGE_MISMATCH -> R.string.integrity_finding_attestation_challenge
    IntegrityFindingCode.ATTESTATION_CHAIN_INVALID -> R.string.integrity_finding_attestation_chain
    IntegrityFindingCode.ATTESTATION_CERT_REVOKED -> R.string.integrity_finding_attestation_revoked
    IntegrityFindingCode.ATTESTATION_ROOT_UNTRUSTED -> R.string.integrity_finding_attestation_root
    IntegrityFindingCode.ATTESTATION_SOFTWARE_ONLY -> R.string.integrity_finding_attestation_software
    IntegrityFindingCode.ATTESTATION_READY -> R.string.integrity_finding_attestation_ready
    IntegrityFindingCode.STRONGBOX_UNAVAILABLE -> R.string.integrity_finding_strongbox_unavailable
    IntegrityFindingCode.STRONGBOX_READY -> R.string.integrity_finding_strongbox_ready
    IntegrityFindingCode.ATTESTATION_BOOT_CROSSCHECK_MISMATCH -> R.string.integrity_finding_attestation_boot_mismatch
    IntegrityFindingCode.ONLINE_ATTESTATION_STATUS_UNAVAILABLE -> R.string.integrity_finding_attestation_online
    IntegrityFindingCode.RKP_PROVISIONING_PRESENT -> R.string.integrity_finding_rkp
}

private fun shortHash(value: String): String = if (value.length > 16) value.take(16) + "…" else value.ifBlank { "—" }

@Composable
private fun yesNo(value: Boolean): String = stringResource(if (value) R.string.integrity_value_yes else R.string.integrity_value_no)

@Composable
private fun nullableYesNo(value: Boolean?): String = when (value) {
    true -> stringResource(R.string.integrity_value_yes)
    false -> stringResource(R.string.integrity_value_no)
    null -> stringResource(R.string.integrity_value_unknown)
}

private fun shareEvidence(context: Context, path: String) {
    val file = File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val mime = when (file.extension.lowercase()) {
        "json" -> "application/json"
        "pem" -> "application/x-pem-file"
        else -> "text/plain"
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.integrity_share_chooser)))
}
