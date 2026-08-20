package com.arthur.roottools.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arthur.roottools.R
import com.arthur.roottools.model.AppControlDetail
import com.arthur.roottools.policy.PackagePolicyController

internal fun LazyListScope.appControlRuntime(
    state: DashboardUiState,
    detail: AppControlDetail,
    onForceStop: (String) -> Unit,
    onBucket: (String, Int) -> Unit,
    onBackground: (String, Boolean) -> Unit,
) {
    val inventory = state.appInventory.apps.firstOrNull { it.packageName == detail.packageName }
    val startup = state.startup.apps.firstOrNull { it.packageName == detail.packageName }
    val permissionOps = state.permissionOpsSnapshot?.takeIf { it.packageName == detail.packageName }
    val runtime = state.appRuntime?.takeIf { it.packageName == detail.packageName }

    item {
        AppControlSectionTitle(
            stringResource(R.string.app_control_runtime_title),
            stringResource(R.string.app_control_runtime_subtitle),
        )
    }
    item {
        val dash = stringResource(R.string.app_control_placeholder_dash)
        AppControlInfoCard {
            AppControlInfoRow(stringResource(R.string.app_control_runtime_running), (runtime?.running ?: inventory?.running ?: startup?.running ?: false).toString())
            AppControlInfoRow(stringResource(R.string.app_control_runtime_stopped_flag), detail.stopped.toString())
            AppControlInfoRow(stringResource(R.string.app_control_runtime_persistent), detail.persistent.toString())
            AppControlInfoRow(stringResource(R.string.app_control_runtime_process_name), detail.processName.ifBlank { dash })
            AppControlInfoRow(stringResource(R.string.app_control_runtime_backend), runtime?.backend?.displayName ?: dash)
            AppControlInfoRow(stringResource(R.string.app_control_runtime_startup_source), state.startup.source)
            AppControlInfoRow(stringResource(R.string.app_control_runtime_startup_count), startup?.startCount?.toString() ?: dash)
            AppControlInfoRow(stringResource(R.string.app_control_runtime_startup_reasons), startup?.startReasons?.joinToString(" · ").orEmpty().ifBlank { dash })
            AppControlInfoRow(stringResource(R.string.app_control_runtime_boot_receivers), startup?.bootReceiverCount?.toString() ?: state.componentSnapshot?.bootReceiverCount?.toString() ?: dash)
            AppControlInfoRow(stringResource(R.string.app_control_runtime_standby_bucket), runtime?.standbyBucket?.toString() ?: startup?.standbyBucket?.toString() ?: dash)
            AppControlInfoRow(stringResource(R.string.app_control_runtime_doze_whitelist), runtime?.dozeWhitelisted?.toString() ?: dash)
            AppControlInfoRow(stringResource(R.string.app_control_runtime_active_services), runtime?.services?.size?.toString() ?: dash)
            AppControlInfoRow(stringResource(R.string.app_control_runtime_foreground_services), runtime?.foregroundServiceCount?.toString() ?: dash)
        }
    }

    item {
        AppControlSectionTitle(
            stringResource(R.string.app_control_processes_title),
            stringResource(R.string.app_control_processes_subtitle),
        )
    }
    if (state.appRuntimeLoading) {
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.app_control_runtime_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } else if (runtime?.processes.isNullOrEmpty()) {
        item { Text(stringResource(R.string.app_control_runtime_no_processes), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        items(runtime.processes, key = { it.pid }) { process ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(process.processName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.app_control_process_meta, process.pid, process.ppid, process.user, process.elapsed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.app_control_process_memory_meta, process.rss, process.memoryPercent),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(stringResource(R.string.app_control_percent, process.cpuPercent), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    item {
        AppControlSectionTitle(
            stringResource(R.string.app_control_services_title),
            stringResource(R.string.app_control_services_subtitle),
        )
    }
    if (runtime?.services.isNullOrEmpty()) {
        item { Text(stringResource(R.string.app_control_runtime_no_services), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        items(runtime.services, key = { it.component }) { service ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(service.component, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        Text(service.processName ?: stringResource(R.string.app_control_default_process), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (service.foreground) AppControlBadge(stringResource(R.string.app_control_badge_fgs))
                }
            }
        }
    }

    if (!runtime?.wakeLockLines.isNullOrEmpty()) {
        item {
            AppControlSectionTitle(
                stringResource(R.string.app_control_wakelock_title),
                stringResource(R.string.app_control_wakelock_subtitle),
            )
        }
        item {
            AppControlInfoCard {
                runtime.wakeLockLines.take(16).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    item {
        AppControlSectionTitle(
            stringResource(R.string.app_control_runtime_actions_title),
            stringResource(R.string.app_control_runtime_actions_subtitle),
        )
    }
    item {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onForceStop(detail.packageName) }, enabled = !state.actionInProgress, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.app_control_action_force_stop)) }
                    OutlinedButton(onClick = { onBucket(detail.packageName, 40) }, enabled = !state.actionInProgress, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.app_control_action_rare)) }
                    OutlinedButton(onClick = { onBucket(detail.packageName, 45) }, enabled = !state.actionInProgress, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.app_control_action_restricted)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onBackground(detail.packageName, true) }, enabled = !state.actionInProgress, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.app_control_action_bg_allow)) }
                    OutlinedButton(onClick = { onBackground(detail.packageName, false) }, enabled = !state.actionInProgress, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.app_control_action_bg_ignore)) }
                }
                permissionOps?.appOps?.filter { it.name == "RUN_IN_BACKGROUND" || it.name == "RUN_ANY_IN_BACKGROUND" }?.forEach { op ->
                    Text(
                        stringResource(R.string.app_control_appop_status, op.name, op.mode ?: stringResource(R.string.app_control_badge_default), op.backend.displayName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    item {
        AppControlSectionTitle(
            stringResource(R.string.app_control_battery_optimization_title),
            stringResource(R.string.app_control_battery_optimization_subtitle),
        )
    }
    item {
        val context = LocalContext.current
        OutlinedButton(
            onClick = { runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.app_control_open_battery_optimization)) }
    }
}

internal fun LazyListScope.appControlStorage(detail: AppControlDetail) {
    item {
        AppControlSectionTitle(
            stringResource(R.string.app_control_storage_title),
            stringResource(R.string.app_control_storage_subtitle),
        )
    }
    item {
        AppControlInfoCard {
            AppControlInfoRow(stringResource(R.string.app_control_storage_base_apk), "${detail.sourceDir}\n${formatAppBytes(detail.baseApkBytes)}")
            AppControlInfoRow(stringResource(R.string.app_control_storage_split_apks), "${detail.splitSourceDirs.size} · ${formatAppBytes(detail.splitApkBytes)}")
            AppControlInfoRow(stringResource(R.string.app_control_storage_total_code), formatAppBytes(detail.totalApkBytes))
            AppControlInfoRow(stringResource(R.string.app_control_storage_source_readable), detail.sourceReadable.toString())
            AppControlInfoRow(stringResource(R.string.app_control_storage_data_dir), detail.dataDir)
            AppControlInfoRow(stringResource(R.string.app_control_storage_credential_protected), detail.credentialProtectedDataDir)
            AppControlInfoRow(stringResource(R.string.app_control_storage_device_protected), detail.deviceProtectedDataDir)
            AppControlInfoRow(stringResource(R.string.app_control_storage_native_library), detail.nativeLibraryDir)
        }
    }
}

internal fun LazyListScope.appControlCode(detail: AppControlDetail) {
    item {
        AppControlSectionTitle(
            stringResource(R.string.app_control_code_title),
            stringResource(R.string.app_control_code_subtitle),
        )
    }
    item {
        val dash = stringResource(R.string.app_control_placeholder_dash)
        AppControlInfoCard {
            AppControlInfoRow(stringResource(R.string.app_control_code_source_status), detail.sourceStatus.displayName)
            AppControlInfoRow(stringResource(R.string.app_control_label_installer), detail.installerPackage ?: dash)
            AppControlInfoRow(stringResource(R.string.app_control_storage_base_apk), detail.sourceDir)
            AppControlInfoRow(stringResource(R.string.app_control_label_splits), detail.splitSourceDirs.ifEmpty { listOf(dash) }.joinToString("\n"))
            AppControlInfoRow(stringResource(R.string.app_control_label_shared_libraries), detail.sharedLibraryFiles.ifEmpty { listOf(dash) }.joinToString("\n"))
            AppControlInfoRow(stringResource(R.string.app_control_label_sha256), detail.signingSha256.ifEmpty { listOf(dash) }.joinToString("\n"))
        }
    }
}

internal fun LazyListScope.appControlPolicy(state: DashboardUiState, detail: AppControlDetail) {
    val audits = state.auditRecords.filter { record ->
        record.target.contains(detail.packageName) || record.before.contains(detail.packageName) || record.after.contains(detail.packageName)
    }.take(20)
    item {
        AppControlSectionTitle(
            stringResource(R.string.app_control_policy_title),
            stringResource(R.string.app_control_policy_subtitle),
        )
    }
    item {
        AppControlInfoCard {
            AppControlInfoRow(stringResource(R.string.app_control_policy_enabled), detail.enabled.toString())
            AppControlInfoRow(stringResource(R.string.app_control_policy_enabled_state), detail.enabledState)
            AppControlInfoRow(stringResource(R.string.app_control_policy_system_app), detail.systemApp.toString())
            AppControlInfoRow(stringResource(R.string.app_control_policy_updated_system), detail.updatedSystemApp.toString())
            AppControlInfoRow(stringResource(R.string.app_control_policy_protected_lifeline), (detail.packageName in PackagePolicyController.PROTECTED_PACKAGES).toString())
            AppControlInfoRow(stringResource(R.string.app_control_policy_allow_backup), detail.allowBackup.toString())
            AppControlInfoRow(stringResource(R.string.app_control_policy_cleartext), detail.usesCleartextTraffic.toString())
            AppControlInfoRow(stringResource(R.string.app_control_policy_debuggable), detail.debuggable.toString())
        }
    }
    item {
        AppControlSectionTitle(
            stringResource(R.string.app_control_audit_title),
            stringResource(R.string.app_control_audit_subtitle),
        )
    }
    if (audits.isEmpty()) {
        item { Text(stringResource(R.string.app_control_audit_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        items(audits, key = { "${it.timestampMs}-${it.action}-${it.target}" }) { record ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.app_control_audit_feature_action, record.feature, record.action), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.app_control_audit_transition, record.before.ifBlank { "—" }, record.after.ifBlank { "—" }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.app_control_audit_source_rollback, record.source, record.rollbackHint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal fun formatAppBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GiB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024))
    bytes >= 1024L -> "%.1f KiB".format(bytes / 1024.0)
    else -> "$bytes B"
}
