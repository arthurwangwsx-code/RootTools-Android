package com.arthur.roottools.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arthur.roottools.R
import com.arthur.roottools.data.AppInventoryQuery
import com.arthur.roottools.model.AppComponentRecord
import com.arthur.roottools.model.AppControlDetail
import com.arthur.roottools.model.AppInventoryFilter
import com.arthur.roottools.model.AppInventoryItem
import com.arthur.roottools.model.AppInventorySort
import com.arthur.roottools.model.ComponentKind
import com.arthur.roottools.model.RuntimePermissionRecord
import com.arthur.roottools.policy.PackagePolicyController
import androidx.core.content.FileProvider
import android.content.Intent
import java.io.File
import java.text.DateFormat
import java.util.Date

private enum class AppControlSection {
    OVERVIEW,
    COMPONENTS,
    OPS,
    PERMISSIONS,
    RUNTIME,
    STORAGE,
    CODE,
    POLICY,
}

@Composable
internal fun AppControlCenterScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectApp: (String) -> Unit,
    onCloseDetail: () -> Unit,
    onFreeze: (String) -> Unit,
    onEnable: (String) -> Unit,
    onForceStop: (String) -> Unit,
    onBucket: (String, Int) -> Unit,
    onBackground: (String, Boolean) -> Unit,
    onSetComponentEnabled: (AppComponentRecord, Boolean) -> Unit,
    onLaunchComponent: (AppComponentRecord) -> Unit,
    onSetRuntimePermission: (String, Boolean) -> Unit,
    onSetAppOpMode: (String, String) -> Unit,
    onLoadAppOps: (String) -> Unit,
    onLoadRuntime: (String) -> Unit,
    onExportDiagnostic: () -> Unit,
) {
    if (state.appControlDetail != null) {
        BackHandler(onBack = onCloseDetail)
        AppControlDetailScreen(
            state = state,
            detail = state.appControlDetail,
            onBack = onCloseDetail,
            onRefresh = { onSelectApp(state.appControlDetail.packageName) },
            onFreeze = onFreeze,
            onEnable = onEnable,
            onForceStop = onForceStop,
            onBucket = onBucket,
            onBackground = onBackground,
            onSetComponentEnabled = onSetComponentEnabled,
            onLaunchComponent = onLaunchComponent,
            onSetRuntimePermission = onSetRuntimePermission,
            onSetAppOpMode = onSetAppOpMode,
            onLoadAppOps = onLoadAppOps,
            onLoadRuntime = onLoadRuntime,
            onExportDiagnostic = onExportDiagnostic,
        )
        return
    }

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppInventoryFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(AppInventorySort.LABEL) }
    val apps = remember(state.appInventory.apps, query, filter, sort) {
        AppInventoryQuery.apply(state.appInventory.apps, query, filter, sort)
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppControlHeader(
                    title = stringResource(R.string.app_control_title),
                    subtitle = stringResource(R.string.app_control_subtitle),
                    onBack = onBack,
                    onRefresh = onRefresh,
                    loading = state.appInventoryLoading,
                )
            }
            item {
                AppInventorySummary(state)
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.app_control_search_label)) },
                    placeholder = { Text(stringResource(R.string.app_control_search_placeholder)) },
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AppInventoryFilter.entries) { item ->
                        FilterChip(
                            selected = filter == item,
                            onClick = { filter = item },
                            label = { Text(appInventoryFilterLabel(item)) },
                        )
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AppInventorySort.entries) { item ->
                        FilterChip(
                            selected = sort == item,
                            onClick = { sort = item },
                            label = { Text(appInventorySortLabel(item)) },
                        )
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.app_control_filtered_count, apps.size, state.appInventory.apps.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.appInventoryLoading && state.appInventory.apps.isEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (apps.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Text(stringResource(R.string.app_control_empty_filter), Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(apps, key = { it.packageName }) { app ->
                    AppInventoryCard(app = app, onClick = { onSelectApp(app.packageName) })
                }
            }
            state.actionMessage?.let { message ->
                item { AppControlMessage(message, error = false) }
            }
            state.error?.let { message ->
                item { AppControlMessage(message, error = true) }
            }
        }
    }
}

@Composable
private fun AppInventorySummary(state: DashboardUiState) {
    val snapshot = state.appInventory
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppControlMetric(stringResource(R.string.app_control_apps_metric), snapshot.apps.size.toString(), stringResource(R.string.app_control_installed_hint), Modifier.weight(1f))
            AppControlMetric(
                stringResource(R.string.app_control_running_metric),
                snapshot.runningApps.toString(),
                stringResource(if (snapshot.runningProbeAvailable) R.string.app_control_live_probe_hint else R.string.app_control_limited_hint),
                Modifier.weight(1f),
            )
            AppControlMetric(stringResource(R.string.app_control_frozen_metric), snapshot.frozenApps.toString(), stringResource(R.string.app_control_disabled_hint), Modifier.weight(1f))
        }
    }
}

@Composable
private fun AppInventoryCard(app: AppInventoryItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = CircleShape,
                ) {
                    Text(
                        app.label.take(1).uppercase(),
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                AppControlBadge(stringResource(if (app.systemApp) R.string.app_control_badge_system else R.string.app_control_badge_user))
            }
            Text(
                stringResource(
                    R.string.app_control_inventory_meta,
                    app.versionName.ifBlank { app.versionCode.toString() },
                    app.uid,
                    app.targetSdk,
                    stringResource(if (app.enabled) R.string.app_control_state_enabled else R.string.app_control_state_frozen),
                    if (app.running) stringResource(R.string.app_control_state_running) else "",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (app.debuggable) AppControlBadge(stringResource(R.string.app_control_badge_debug))
                if (app.hasSplits) AppControlBadge(stringResource(R.string.app_control_badge_splits, app.splitCount))
                if (app.updatedSystemApp) AppControlBadge(stringResource(R.string.app_control_badge_updated_system))
            }
        }
    }
}

@Composable
private fun AppControlDetailScreen(
    state: DashboardUiState,
    detail: AppControlDetail,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onFreeze: (String) -> Unit,
    onEnable: (String) -> Unit,
    onForceStop: (String) -> Unit,
    onBucket: (String, Int) -> Unit,
    onBackground: (String, Boolean) -> Unit,
    onSetComponentEnabled: (AppComponentRecord, Boolean) -> Unit,
    onLaunchComponent: (AppComponentRecord) -> Unit,
    onSetRuntimePermission: (String, Boolean) -> Unit,
    onSetAppOpMode: (String, String) -> Unit,
    onLoadAppOps: (String) -> Unit,
    onLoadRuntime: (String) -> Unit,
    onExportDiagnostic: () -> Unit,
) {
    var section by rememberSaveable(detail.packageName) { mutableStateOf(AppControlSection.OVERVIEW) }
    var pendingFreeze by remember { mutableStateOf(false) }
    var pendingComponentDisable by remember { mutableStateOf<AppComponentRecord?>(null) }
    var componentQuery by rememberSaveable(detail.packageName) { mutableStateOf("") }
    var componentKind by rememberSaveable(detail.packageName) { mutableStateOf<ComponentKind?>(null) }
    var componentFlags by rememberSaveable(detail.packageName) { mutableStateOf("ALL") }
    val protected = detail.packageName in PackagePolicyController.PROTECTED_PACKAGES

    LaunchedEffect(section, detail.packageName) {
        if (section == AppControlSection.OPS && state.permissionOpsSnapshot?.appOps.isNullOrEmpty()) {
            onLoadAppOps(detail.packageName)
        }
        if (section == AppControlSection.RUNTIME && state.appRuntime?.packageName != detail.packageName) {
            onLoadRuntime(detail.packageName)
        }
    }

    if (pendingFreeze) {
        AlertDialog(
            onDismissRequest = { pendingFreeze = false },
            title = { Text(stringResource(R.string.app_control_freeze_confirm_title, detail.label)) },
            text = { Text(stringResource(R.string.app_control_freeze_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { pendingFreeze = false; onFreeze(detail.packageName) }) { Text(stringResource(R.string.app_control_action_freeze)) }
            },
            dismissButton = { TextButton(onClick = { pendingFreeze = false }) { Text(stringResource(R.string.app_control_action_cancel)) } },
        )
    }
    pendingComponentDisable?.let { component ->
        AlertDialog(
            onDismissRequest = { pendingComponentDisable = null },
            title = { Text(stringResource(R.string.app_control_component_disable_title, componentKindLabel(component.kind))) },
            text = { Text(component.className) },
            confirmButton = {
                TextButton(onClick = { pendingComponentDisable = null; onSetComponentEnabled(component, false) }) { Text(stringResource(R.string.app_control_component_disable_action)) }
            },
            dismissButton = { TextButton(onClick = { pendingComponentDisable = null }) { Text(stringResource(R.string.app_control_action_cancel)) } },
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppControlHeader(
                    title = detail.label,
                    subtitle = detail.packageName,
                    onBack = onBack,
                    onRefresh = onRefresh,
                    loading = state.appControlDetailLoading || state.actionInProgress,
                )
            }
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(
                                        R.string.app_control_app_identity,
                                        stringResource(if (detail.systemApp) R.string.app_control_type_system else R.string.app_control_type_user),
                                        detail.uid,
                                    ),
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    stringResource(R.string.app_control_version_sdk, detail.versionName, detail.versionCode, detail.minSdk, detail.targetSdk),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            AppControlBadge(stringResource(if (detail.enabled) R.string.app_control_badge_enabled else R.string.app_control_badge_frozen))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (detail.enabled && !protected) Button(onClick = { pendingFreeze = true }, enabled = !state.actionInProgress) { Text(stringResource(R.string.app_control_action_freeze)) }
                            if (!detail.enabled) Button(onClick = { onEnable(detail.packageName) }, enabled = !state.actionInProgress) { Text(stringResource(R.string.app_control_action_enable)) }
                            if (detail.enabled && !protected) OutlinedButton(onClick = { onForceStop(detail.packageName) }, enabled = !state.actionInProgress) { Text(stringResource(R.string.app_control_action_stop)) }
                            if (protected) AppControlBadge(stringResource(R.string.app_control_badge_protected))
                        }
                        if (detail.enabled && !protected) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item { TextButton(onClick = { onBucket(detail.packageName, 40) }) { Text(stringResource(R.string.app_control_action_rare)) } }
                                item { TextButton(onClick = { onBucket(detail.packageName, 45) }) { Text(stringResource(R.string.app_control_action_restricted)) } }
                                item { TextButton(onClick = { onBackground(detail.packageName, true) }) { Text(stringResource(R.string.app_control_action_bg_allow)) } }
                                item { TextButton(onClick = { onBackground(detail.packageName, false) }) { Text(stringResource(R.string.app_control_action_bg_ignore)) } }
                            }
                        }
                        OutlinedButton(onClick = onExportDiagnostic, enabled = !state.actionInProgress) {
                            Text(stringResource(R.string.app_control_action_export_diagnostic))
                        }
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AppControlSection.entries) { item ->
                        FilterChip(
                            selected = section == item,
                            onClick = { section = item },
                            label = { Text(appControlSectionLabel(item)) },
                        )
                    }
                }
            }
            when (section) {
                AppControlSection.OVERVIEW -> appControlOverview(state, detail, onExportDiagnostic)
                AppControlSection.COMPONENTS -> appControlComponents(
                    state = state,
                    packageName = detail.packageName,
                    query = componentQuery,
                    selectedKind = componentKind,
                    flagFilter = componentFlags,
                    onQueryChange = { componentQuery = it },
                    onKindChange = { componentKind = it },
                    onFlagChange = { componentFlags = it },
                    onSetComponentEnabled = { component, enabled ->
                        if (enabled) onSetComponentEnabled(component, true) else pendingComponentDisable = component
                    },
                    onLaunchComponent = onLaunchComponent,
                )
                AppControlSection.PERMISSIONS -> appControlPermissions(
                    state = state,
                    packageName = detail.packageName,
                    onSetRuntimePermission = onSetRuntimePermission,
                )
                AppControlSection.OPS -> appControlAppOps(state, detail.packageName, onSetAppOpMode)
                AppControlSection.RUNTIME -> appControlRuntime(state, detail, onForceStop, onBucket, onBackground)
                AppControlSection.STORAGE -> appControlStorage(detail)
                AppControlSection.CODE -> appControlCode(detail)
                AppControlSection.POLICY -> appControlPolicy(state, detail)
            }
            state.actionMessage?.let { message -> item { AppControlMessage(message, error = false) } }
            state.error?.let { message -> item { AppControlMessage(message, error = true) } }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.appControlOverview(
    state: DashboardUiState,
    detail: AppControlDetail,
    onExportDiagnostic: () -> Unit,
) {
    item { AppControlSectionTitle(stringResource(R.string.app_control_info_title), stringResource(R.string.app_control_info_subtitle)) }
    item {
        AppControlInfoCard {
            AppControlInfoRow(stringResource(R.string.app_control_label_package), detail.packageName)
            AppControlInfoRow(stringResource(R.string.app_control_label_uid), detail.uid.toString())
            AppControlInfoRow(stringResource(R.string.app_control_label_version), "${detail.versionName} (${detail.versionCode})")
            AppControlInfoRow(
                stringResource(R.string.app_control_label_sdk),
                stringResource(
                    R.string.app_control_sdk_value,
                    detail.minSdk,
                    detail.targetSdk,
                    detail.compileSdk?.let { stringResource(R.string.app_control_compile_sdk_suffix, it) }.orEmpty(),
                ),
            )
            AppControlInfoRow(stringResource(R.string.app_control_label_installed), formatAppTime(detail.firstInstallTimeMs))
            AppControlInfoRow(stringResource(R.string.app_control_label_updated), formatAppTime(detail.lastUpdateTimeMs))
            AppControlInfoRow(stringResource(R.string.app_control_label_installer), detail.installerPackage ?: stringResource(R.string.app_control_placeholder_dash))
            AppControlInfoRow("Source", detail.sourceStatus.displayName)
            AppControlInfoRow("Code size", formatAppBytes(detail.totalApkBytes))
            AppControlInfoRow(stringResource(R.string.app_control_label_state), detail.enabledState)
            AppControlInfoRow(stringResource(R.string.app_control_label_flags), buildList {
                if (detail.debuggable) add("debuggable")
                if (detail.persistent) add("persistent")
                if (detail.largeHeap) add("largeHeap")
                if (detail.allowBackup) add("allowBackup")
                if (detail.usesCleartextTraffic) add("cleartext")
            }.ifEmpty { listOf(stringResource(R.string.app_control_none)) }.joinToString(" · "))
        }
    }
    item { AppControlSectionTitle(stringResource(R.string.app_control_component_summary_title), stringResource(R.string.app_control_component_summary_subtitle)) }
    item {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val componentsHint = stringResource(R.string.app_control_components_hint)
                AppControlMetric(stringResource(R.string.app_control_component_activity), detail.activityCount.toString(), componentsHint, Modifier.weight(1f))
                AppControlMetric(stringResource(R.string.app_control_component_service), detail.serviceCount.toString(), componentsHint, Modifier.weight(1f))
                AppControlMetric(stringResource(R.string.app_control_component_receiver), detail.receiverCount.toString(), componentsHint, Modifier.weight(1f))
                AppControlMetric(stringResource(R.string.app_control_component_provider), detail.providerCount.toString(), componentsHint, Modifier.weight(1f))
            }
        }
    }
    item { AppControlSectionTitle(stringResource(R.string.app_control_paths_title), stringResource(R.string.app_control_paths_subtitle)) }
    item {
        AppControlInfoCard {
            AppControlInfoRow(stringResource(R.string.app_control_label_apk), detail.sourceDir)
            AppControlInfoRow(stringResource(R.string.app_control_label_splits), if (detail.splitSourceDirs.isEmpty()) stringResource(R.string.app_control_placeholder_dash) else detail.splitSourceDirs.joinToString("\n"))
            AppControlInfoRow(stringResource(R.string.app_control_label_data), detail.dataDir)
            AppControlInfoRow(stringResource(R.string.app_control_label_device_protected), detail.deviceProtectedDataDir)
            AppControlInfoRow(stringResource(R.string.app_control_label_credential_protected), detail.credentialProtectedDataDir)
            AppControlInfoRow(stringResource(R.string.app_control_label_native_libs), detail.nativeLibraryDir)
        }
    }
    item { AppControlSectionTitle(stringResource(R.string.app_control_signature_title), stringResource(R.string.app_control_signature_subtitle)) }
    item {
        AppControlInfoCard {
            AppControlInfoRow(stringResource(R.string.app_control_label_sha256), detail.signingSha256.ifEmpty { listOf(stringResource(R.string.app_control_placeholder_dash)) }.joinToString("\n"))
            AppControlInfoRow(stringResource(R.string.app_control_label_shared_libraries), detail.sharedLibraryFiles.ifEmpty { listOf(stringResource(R.string.app_control_placeholder_dash)) }.joinToString("\n"))
        }
    }
    item {
        AppControlSectionTitle(
            stringResource(R.string.app_control_diagnostic_title),
            stringResource(R.string.app_control_diagnostic_subtitle),
        )
    }
    item {
        val context = LocalContext.current
        AppControlInfoCard {
            Button(onClick = onExportDiagnostic, enabled = !state.actionInProgress, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.app_control_diagnostic_export))
            }
            state.appControlExport?.let { export ->
                Text(export.markdownPath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { shareAppControlFile(context, export.markdownPath, "text/markdown") }) { Text(stringResource(R.string.app_control_diagnostic_share_markdown)) }
                    OutlinedButton(onClick = { shareAppControlFile(context, export.jsonPath, "application/json") }) { Text(stringResource(R.string.app_control_diagnostic_share_json)) }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.appControlComponents(
    state: DashboardUiState,
    packageName: String,
    query: String,
    selectedKind: ComponentKind?,
    flagFilter: String,
    onQueryChange: (String) -> Unit,
    onKindChange: (ComponentKind?) -> Unit,
    onFlagChange: (String) -> Unit,
    onSetComponentEnabled: (AppComponentRecord, Boolean) -> Unit,
    onLaunchComponent: (AppComponentRecord) -> Unit,
) {
    val snapshot = state.componentSnapshot?.takeIf { it.packageName == packageName }
    item { AppControlSectionTitle(stringResource(R.string.app_control_components_title), stringResource(R.string.app_control_components_subtitle)) }
    if (snapshot == null) {
        item { Text(stringResource(R.string.app_control_components_loading), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    item {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.app_control_component_search_label)) },
            placeholder = { Text(stringResource(R.string.app_control_component_search_placeholder)) },
        )
    }
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            item {
                FilterChip(selected = selectedKind == null, onClick = { onKindChange(null) }, label = { Text(stringResource(R.string.app_control_filter_all_caps)) })
            }
            items(ComponentKind.entries) { kind ->
                FilterChip(selected = selectedKind == kind, onClick = { onKindChange(kind) }, label = { Text(componentKindLabel(kind)) })
            }
        }
    }
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("ALL", "BOOT", "EXPORTED", "FGS", "DISABLED").forEach { value ->
                item {
                    FilterChip(
                        selected = flagFilter == value,
                        onClick = { onFlagChange(value) },
                        label = { Text(componentFlagLabel(value)) },
                    )
                }
            }
        }
    }
    val normalized = query.trim().lowercase()
    val filtered = snapshot.components.filter { component ->
        (selectedKind == null || component.kind == selectedKind) &&
            (normalized.isBlank() || component.className.lowercase().contains(normalized) || component.componentName.lowercase().contains(normalized)) &&
            when (flagFilter) {
                "BOOT" -> component.bootReceiver
                "EXPORTED" -> component.exported
                "FGS" -> component.foregroundService
                "DISABLED" -> !component.enabled
                else -> true
            }
    }
    ComponentKind.entries.filter { selectedKind == null || selectedKind == it }.forEach { kind ->
        val components = filtered.filter { it.kind == kind }
        item {
            AppControlSectionTitle(
                componentKindLabel(kind),
                stringResource(R.string.app_control_component_count, components.size, components.count { !it.enabled }),
            )
        }
        if (components.isEmpty()) {
            item { Text(stringResource(R.string.app_control_component_empty, componentKindLabel(kind)), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(components, key = { it.componentName }) { component ->
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(component.className.substringAfterLast('.'), fontWeight = FontWeight.Medium)
                            Text(component.className, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (component.exported) AppControlBadge(stringResource(R.string.app_control_badge_exported))
                                if (component.bootReceiver) AppControlBadge(stringResource(R.string.app_control_badge_boot))
                                if (component.foregroundService) AppControlBadge(stringResource(R.string.app_control_badge_fgs))
                                if (component.directBootAware) AppControlBadge(stringResource(R.string.app_control_badge_direct_boot))
                            }
                            component.permission?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (component.protectedReason != null) AppControlBadge(stringResource(R.string.app_control_badge_core))
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Switch(
                                checked = component.enabled,
                                onCheckedChange = { onSetComponentEnabled(component, it) },
                                enabled = component.protectedReason == null && !state.actionInProgress,
                            )
                            if (component.kind == ComponentKind.ACTIVITY && component.enabled) {
                                TextButton(onClick = { onLaunchComponent(component) }, enabled = !state.actionInProgress) { Text(stringResource(R.string.app_control_action_launch)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.appControlPermissions(
    state: DashboardUiState,
    packageName: String,
    onSetRuntimePermission: (String, Boolean) -> Unit,
) {
    val snapshot = state.permissionOpsSnapshot?.takeIf { it.packageName == packageName }
    item { AppControlSectionTitle(stringResource(R.string.app_control_permissions_title), stringResource(R.string.app_control_permissions_subtitle)) }
    if (snapshot == null) {
        item { Text(stringResource(R.string.app_control_permissions_loading), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    item {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppControlMetric(stringResource(R.string.app_control_metric_granted), snapshot.grantedPermissions.toString(), stringResource(R.string.app_control_permissions_hint), Modifier.weight(1f))
                AppControlMetric(stringResource(R.string.app_control_metric_denied), snapshot.deniedPermissions.toString(), stringResource(R.string.app_control_permissions_hint), Modifier.weight(1f))
                AppControlMetric(stringResource(R.string.app_control_metric_total), snapshot.permissions.size.toString(), stringResource(R.string.app_control_requested_hint), Modifier.weight(1f))
            }
        }
    }
    items(snapshot.permissions, key = { it.name }) { permission ->
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(permission.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(permission.protection, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    AppControlBadge(stringResource(if (permission.granted) R.string.app_control_badge_granted else R.string.app_control_badge_denied))
                    if (permission.protection == "dangerous") {
                        TextButton(
                            onClick = { onSetRuntimePermission(permission.name, !permission.granted) },
                            enabled = !state.actionInProgress && (state.snapshot.rootAvailable || state.shizuku.ready),
                        ) { Text(stringResource(if (permission.granted) R.string.app_control_action_revoke else R.string.app_control_action_grant)) }
                    }
                }
            }
        }
    }
    item { AppControlSectionTitle(stringResource(R.string.app_control_special_access_title), stringResource(R.string.app_control_special_access_subtitle)) }
    item {
        val context = androidx.compose.ui.platform.LocalContext.current
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
                    }
                }) { Text(stringResource(R.string.app_control_special_app_details)) }
            }
            item {
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
                    }
                }) { Text(stringResource(R.string.app_control_special_overlay)) }
            }
            item {
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                }) { Text(stringResource(R.string.app_control_special_usage)) }
            }
            item {
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                }) { Text(stringResource(R.string.app_control_special_notification)) }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.appControlAppOps(
    state: DashboardUiState,
    packageName: String,
    onSetAppOpMode: (String, String) -> Unit,
) {
    val snapshot = state.permissionOpsSnapshot?.takeIf { it.packageName == packageName }
    item { AppControlSectionTitle(stringResource(R.string.app_control_appops_title), stringResource(R.string.app_control_appops_subtitle)) }
    if (snapshot == null) {
        item { Text(stringResource(R.string.app_control_appops_loading), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    if (!snapshot.appOpsBackendAvailable) {
        item { AppControlMessage(stringResource(R.string.app_control_appops_unavailable), error = true) }
        return
    }
    items(snapshot.appOps, key = { it.name }) { op ->
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(op.name, fontWeight = FontWeight.Medium)
                        Text(op.raw.ifBlank { stringResource(R.string.app_control_appops_no_explicit_state) }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    AppControlBadge(op.mode?.uppercase() ?: stringResource(if (op.supported) R.string.app_control_badge_default else R.string.app_control_badge_na))
                }
                if (op.supported) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("allow", "ignore", "default").forEach { mode ->
                            item {
                                FilterChip(
                                    selected = op.mode == mode,
                                    onClick = { onSetAppOpMode(op.name, mode) },
                                    enabled = !state.actionInProgress,
                                    label = { Text(mode) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppControlHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    loading: Boolean,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.app_control_back)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (loading) CircularProgressIndicator(modifier = Modifier.padding(10.dp))
        else IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.app_control_refresh)) }
    }
}

@Composable
internal fun AppControlSectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun AppControlInfoCard(content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
internal fun AppControlInfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(value.ifBlank { stringResource(R.string.app_control_placeholder_dash) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
    }
}

@Composable
private fun AppControlMetric(label: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun AppControlBadge(text: String) {
    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), shape = RoundedCornerShape(50)) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AppControlMessage(message: String, error: Boolean) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            message,
            Modifier.padding(12.dp),
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun appInventoryFilterLabel(filter: AppInventoryFilter): String = stringResource(
    when (filter) {
        AppInventoryFilter.ALL -> R.string.app_control_filter_all
        AppInventoryFilter.USER -> R.string.app_control_filter_user
        AppInventoryFilter.SYSTEM -> R.string.app_control_filter_system
        AppInventoryFilter.RUNNING -> R.string.app_control_filter_running
        AppInventoryFilter.FROZEN -> R.string.app_control_filter_frozen
        AppInventoryFilter.DEBUGGABLE -> R.string.app_control_filter_debuggable
    }
)

@Composable
private fun appInventorySortLabel(sort: AppInventorySort): String = stringResource(
    when (sort) {
        AppInventorySort.LABEL -> R.string.app_control_sort_label
        AppInventorySort.PACKAGE -> R.string.app_control_sort_package
        AppInventorySort.LAST_UPDATE -> R.string.app_control_sort_update
        AppInventorySort.TARGET_SDK -> R.string.app_control_sort_target_sdk
    }
)

@Composable
private fun appControlSectionLabel(section: AppControlSection): String = stringResource(
    when (section) {
        AppControlSection.OVERVIEW -> R.string.app_control_section_overview
        AppControlSection.COMPONENTS -> R.string.app_control_section_components
        AppControlSection.OPS -> R.string.app_control_section_appops
        AppControlSection.PERMISSIONS -> R.string.app_control_section_permissions
        AppControlSection.RUNTIME -> R.string.app_control_section_runtime
        AppControlSection.STORAGE -> R.string.app_control_section_storage
        AppControlSection.CODE -> R.string.app_control_section_code
        AppControlSection.POLICY -> R.string.app_control_section_policy
    }
)

@Composable
private fun componentKindLabel(kind: ComponentKind): String = stringResource(
    when (kind) {
        ComponentKind.ACTIVITY -> R.string.app_control_component_activity
        ComponentKind.SERVICE -> R.string.app_control_component_service
        ComponentKind.RECEIVER -> R.string.app_control_component_receiver
        ComponentKind.PROVIDER -> R.string.app_control_component_provider
    }
)

@Composable
private fun componentFlagLabel(flag: String): String = stringResource(
    when (flag) {
        "BOOT" -> R.string.app_control_filter_boot
        "EXPORTED" -> R.string.app_control_filter_exported
        "FGS" -> R.string.app_control_filter_fgs
        "DISABLED" -> R.string.app_control_filter_disabled_caps
        else -> R.string.app_control_filter_all_caps
    }
)

private fun formatAppTime(timestamp: Long): String = if (timestamp <= 0L) "" else DateFormat.getDateTimeInstance().format(Date(timestamp))

private fun shareAppControlFile(context: android.content.Context, path: String, mime: String) {
    val file = File(path)
    if (!file.isFile) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, context.getString(R.string.app_control_diagnostic_share_title))) }
}
