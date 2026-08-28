package com.arthur.roottools.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import com.arthur.roottools.R
import com.arthur.roottools.model.CpuCluster
import com.arthur.roottools.core.ui.action.copyToClipboard
import com.arthur.roottools.core.ui.action.openPackage
import com.arthur.roottools.core.ui.action.shareDiagnosticFile
import com.arthur.roottools.core.ui.action.shareText
import com.arthur.roottools.core.ui.chart.RootToolsTemperatureSparkline
import com.arthur.roottools.app.navigation.ToolCapability
import com.arthur.roottools.app.navigation.ToolDefinition
import com.arthur.roottools.app.navigation.ToolId
import com.arthur.roottools.app.navigation.ToolRegistry
import com.arthur.roottools.app.navigation.ToolboxCard
import com.arthur.roottools.app.navigation.ToolboxRoute
import com.arthur.roottools.app.navigation.routeFor
import com.arthur.roottools.core.presentation.formatGHz
import com.arthur.roottools.core.presentation.formatMemoryKb
import com.arthur.roottools.core.presentation.formatRelativeTime
import com.arthur.roottools.core.presentation.formatStartupSeconds
import com.arthur.roottools.core.presentation.formatUptime
import com.arthur.roottools.feature.dashboard.presentation.categoryOrder
import com.arthur.roottools.feature.dashboard.presentation.frequencyBins
import com.arthur.roottools.feature.dashboard.presentation.rangeText
import com.arthur.roottools.feature.dashboard.presentation.thermalStageLabel
import com.arthur.roottools.feature.dashboard.ui.DailyHealthHistoryCard
import com.arthur.roottools.feature.dashboard.ui.HealthDashboardScreen
import com.arthur.roottools.feature.dashboard.ui.SamplingIntervalCard
import com.arthur.roottools.feature.diagnostics.ui.LagForensicsCard
import com.arthur.roottools.feature.performance.ui.PerformanceScreen as FeaturePerformanceScreen
import com.arthur.roottools.model.AppPolicyCategory
import com.arthur.roottools.model.AppComponentRecord
import com.arthur.roottools.model.AppOpRecord
import com.arthur.roottools.model.ComponentKind
import com.arthur.roottools.model.AdbEndpoint
import com.arthur.roottools.model.AdbEndpointType
import com.arthur.roottools.model.DeviceHealthSnapshot
import com.arthur.roottools.model.DeviceSnapshot
import com.arthur.roottools.model.HealthHistoryPoint
import com.arthur.roottools.model.MemoryPressureStatus
import com.arthur.roottools.model.MagiskModuleInfo
import com.arthur.roottools.model.NetworkSnapshot
import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.model.ProcessHealth
import com.arthur.roottools.model.RootShellRecord
import com.arthur.roottools.model.StartupAppRecord
import com.arthur.roottools.model.StorageSnapshot
import com.arthur.roottools.model.StorageStatus
import com.arthur.roottools.model.SystemActionId
import com.arthur.roottools.model.ThermalStage
import com.arthur.roottools.model.VectorModuleInfo
import com.arthur.roottools.core.ui.component.RootToolsErrorCard as ErrorCard
import com.arthur.roottools.core.ui.component.RootToolsDetailHeader as DetailHeader
import com.arthur.roottools.core.ui.component.RootToolsKeyValueRow as SummaryRow
import com.arthur.roottools.core.ui.component.RootToolsMetricTile as HealthMetric
import com.arthur.roottools.core.ui.component.RootToolsSectionHeader as SectionLabel
import com.arthur.roottools.core.ui.action.copyToClipboard
import com.arthur.roottools.core.ui.action.openPackage
import com.arthur.roottools.core.ui.action.shareDiagnosticFile
import com.arthur.roottools.core.ui.action.shareText
import com.arthur.roottools.feature.dashboard.presentation.categoryOrder
import com.arthur.roottools.core.presentation.formatClockTime
import com.arthur.roottools.core.presentation.formatGHz
import com.arthur.roottools.core.presentation.formatMemoryKb
import com.arthur.roottools.core.presentation.formatRelativeTime
import com.arthur.roottools.core.presentation.formatStartupSeconds
import com.arthur.roottools.core.presentation.formatUptime
import com.arthur.roottools.feature.dashboard.presentation.frequencyBins
import com.arthur.roottools.feature.dashboard.presentation.rangeText
import com.arthur.roottools.feature.dashboard.presentation.thermalStageLabel
import com.arthur.roottools.feature.developer.DeveloperRuntimeRoute
import com.arthur.roottools.feature.integrity.ui.EnvironmentIntegrityRoute
import com.arthur.roottools.feature.network.tailscale.RootTailscaleRoute
import com.arthur.roottools.app.shadow.ShadowDisplayRoute
import com.arthur.roottools.app.adgovernance.AdGovernanceRoute
import com.arthur.roottools.app.agent.AgentSessionRoute
import com.arthur.roottools.app.assistant.AssistantSettingsRoute
import java.io.File
import kotlin.math.roundToInt

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel,
    openAdbOnStart: Boolean = false,
    openIntegrityOnStart: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var route by rememberSaveable {
        mutableStateOf(
            when {
                openAdbOnStart -> ToolboxRoute.ADB
                openIntegrityOnStart -> ToolboxRoute.INTEGRITY
                else -> ToolboxRoute.HOME
            }
        )
    }

    LaunchedEffect(route) {
        viewModel.setDashboardSampling(route == ToolboxRoute.DASHBOARD || route == ToolboxRoute.BATTERY)
        if (route == ToolboxRoute.PERFORMANCE) viewModel.loadPerformanceExplain()
        if (route == ToolboxRoute.ADB) viewModel.loadAdb()
        if (route == ToolboxRoute.PERMISSIONS) viewModel.loadModules()
        if (route == ToolboxRoute.STARTUP) viewModel.loadStartup()
        if (route == ToolboxRoute.APPS) viewModel.loadAppControl()
        if (route == ToolboxRoute.DIAGNOSTICS) viewModel.loadDiagnostics()
        if (route == ToolboxRoute.MODULES) viewModel.loadModules()
        if (route == ToolboxRoute.ACTIONS) viewModel.loadAudit()
        if (route == ToolboxRoute.NETWORK) viewModel.loadNetwork()
        if (route == ToolboxRoute.STORAGE) viewModel.loadStorage()
    }

    BackHandler(enabled = route != ToolboxRoute.HOME) {
        route = ToolboxRoute.HOME
    }

    when (route) {
        ToolboxRoute.HOME -> ToolboxHomeScreen(
            state = state,
            onRefresh = viewModel::refresh,
            onNavigate = { route = it },
        )
        ToolboxRoute.DASHBOARD -> HealthDashboardScreen(
            state = state.toHealthDashboardUiState(),
            onBack = { route = ToolboxRoute.HOME },
            onRefresh = viewModel::refresh,
            onSamplingSeconds = viewModel::setDetailSamplingSeconds,
        )
        ToolboxRoute.PERFORMANCE -> FeaturePerformanceScreen(
            state = state.toPerformanceUiState(),
            onBack = { route = ToolboxRoute.HOME },
            onRefresh = viewModel::refresh,
            onModeSelected = viewModel::setMode,
            onReleaseCaps = viewModel::releaseRootToolsCpuCaps,
        )
        ToolboxRoute.SHADOW_DISPLAY -> ShadowDisplayRoute(
            onBack = { route = ToolboxRoute.HOME },
        )
        ToolboxRoute.AGENT_SESSION -> AgentSessionRoute(
            onBack = { route = ToolboxRoute.HOME },
        )
        ToolboxRoute.ADB -> AdbScreen(
            state = state,
            onBack = { route = ToolboxRoute.HOME },
            onRefresh = viewModel::loadAdb,
            onAdbToggle = viewModel::toggleAdb,
            onNativeWirelessToggle = viewModel::setNativeWireless,
            onRootBootRestore = { viewModel.setAdbBootPolicy(restoreRootTcp = it) },
            onNativeBootRestore = { viewModel.setAdbBootPolicy(restoreNativeWireless = it) },
        )
        ToolboxRoute.ROOT_TAILSCALE -> RootTailscaleRoute(
            onBack = { route = ToolboxRoute.HOME },
        )
        ToolboxRoute.PERMISSIONS -> PermissionScreen(
            state = state,
            onBack = { route = ToolboxRoute.HOME },
            onRefresh = viewModel::refresh,
            onRequestRoot = viewModel::requestRoot,
            onRequestShizuku = viewModel::requestShizukuPermission,
        )
        ToolboxRoute.STARTUP -> StartupScreen(
            state = state,
            onBack = { route = ToolboxRoute.HOME },
            onRefresh = viewModel::loadStartup,
        )
        ToolboxRoute.APPS -> AppControlCenterScreen(
            state = state,
            onBack = { route = ToolboxRoute.HOME },
            onRefresh = viewModel::loadAppControl,
            onSelectApp = viewModel::loadAppControlDetail,
            onCloseDetail = viewModel::clearAppControlDetail,
            onFreeze = viewModel::freezePackage,
            onEnable = viewModel::enablePackage,
            onForceStop = viewModel::forceStopPackage,
            onBucket = viewModel::setPackageBucket,
            onBackground = viewModel::setPackageBackground,
            onSetComponentEnabled = viewModel::setComponentEnabled,
            onLaunchComponent = viewModel::launchComponent,
            onSetRuntimePermission = viewModel::setRuntimePermission,
            onSetAppOpMode = viewModel::setAppOpMode,
            onLoadAppOps = viewModel::loadPermissionAppOps,
            onLoadRuntime = viewModel::loadAppRuntime,
            onExportDiagnostic = viewModel::exportAppControlDiagnostic,
        )
        ToolboxRoute.DIAGNOSTICS -> DiagnosticsScreen(
            state = state,
            onBack = { route = ToolboxRoute.HOME },
            onRefresh = viewModel::loadDiagnostics,
            onAttributeRootShell = viewModel::attributeRootShell,
        )
        ToolboxRoute.MODULES -> ModuleCenterScreen(
            state = state,
            onBack = { route = ToolboxRoute.HOME },
            onRefresh = viewModel::loadModules,
            onMagiskToggle = viewModel::setMagiskModuleEnabled,
            onVectorToggle = viewModel::setVectorModuleEnabled,
            onLoadScope = viewModel::loadVectorScope,
        )
        ToolboxRoute.ACTIONS -> CommonActionsScreen(
            state = state,
            onBack = { route = ToolboxRoute.HOME },
            onRunAction = viewModel::runSystemAction,
            onModeSelected = viewModel::setMode,
            onExportDiagnostic = viewModel::exportDiagnosticReport,
            onFavorite = viewModel::setActionFavorite,
        )
        ToolboxRoute.NETWORK -> NetworkDiagnosticsScreen(
            state = state,
            onBack = { route = ToolboxRoute.HOME },
            onRefresh = viewModel::loadNetwork,
            onPing = viewModel::pingNetworkTarget,
        )
        ToolboxRoute.STORAGE -> StorageDiagnosticsScreen(
            state = state,
            onBack = { route = ToolboxRoute.HOME },
            onRefresh = viewModel::loadStorage,
        )
        ToolboxRoute.BATTERY -> BatteryThermalScreen(
            state = state,
            onBack = { route = ToolboxRoute.HOME },
            onProtectionToggle = viewModel::setBatteryProtection,
            onSamplingSeconds = viewModel::setDetailSamplingSeconds,
        )
        ToolboxRoute.SHIZUKU -> ShizukuSuiScreen(
            state = state,
            onBack = { route = ToolboxRoute.HOME },
            onRefresh = viewModel::refreshShizuku,
            onRequestPermission = viewModel::requestShizukuPermission,
            onSelfTest = viewModel::runShizukuSelfTest,
        )
        ToolboxRoute.COMPONENTS -> ComponentManagerScreen(
            state = state,
            onBack = { route = ToolboxRoute.HOME },
            onLoad = viewModel::loadComponents,
            onSetEnabled = viewModel::setComponentEnabled,
        )
        ToolboxRoute.PERMISSION_OPS -> PermissionAppOpsScreen(
            state = state,
            onBack = { route = ToolboxRoute.HOME },
            onLoad = viewModel::loadPermissionAppOps,
            onSetMode = viewModel::setAppOpMode,
        )
        ToolboxRoute.INTEGRITY -> EnvironmentIntegrityRoute(
            onBack = { route = ToolboxRoute.HOME },
        )
        ToolboxRoute.ASSISTANT -> AssistantSettingsRoute(
            onBack = { route = ToolboxRoute.HOME },
        )
        ToolboxRoute.DEVELOPER_RUNTIME -> DeveloperRuntimeRoute(
            onBack = { route = ToolboxRoute.HOME },
        )
        ToolboxRoute.AD_GOVERNANCE -> AdGovernanceRoute(
            onBack = { route = ToolboxRoute.HOME },
        )
    }
}

@Composable
private fun ToolboxHomeScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onNavigate: (ToolboxRoute) -> Unit,
) {
    val snapshot = state.snapshot
    val health = state.health
    val shadowDisplaySubtitle = stringResource(R.string.shadow_display_home_subtitle)
    val shadowDisplayBadge = stringResource(R.string.shadow_display_home_badge)
    val agentSessionSubtitle = stringResource(R.string.agent_session_home_subtitle)
    val agentSessionBadge = stringResource(R.string.agent_overlay_short_label)
    val adGovernanceSubtitle = stringResource(R.string.ad_governance_home_subtitle)
    val adGovernanceBadge = stringResource(R.string.ad_governance_home_badge)
    val cards = ToolRegistry.tools.map { definition ->
        buildToolboxCard(
            definition = definition,
            title = stringResource(definition.titleRes),
            state = state,
            shadowDisplaySubtitle = shadowDisplaySubtitle,
            shadowDisplayBadge = shadowDisplayBadge,
            agentSessionSubtitle = agentSessionSubtitle,
            agentSessionBadge = agentSessionBadge,
            adGovernanceSubtitle = adGovernanceSubtitle,
            adGovernanceBadge = adGovernanceBadge,
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            item {
                Header(
                    model = snapshot.model,
                    rootAvailable = snapshot.rootAvailable,
                    loading = state.loading,
                    onRefresh = onRefresh,
                )
            }
            item { ToolboxHero(snapshot, state.mode) }
            item { SectionLabel("工具箱", "每个能力独立成卡片，后续可以持续扩展") }
            items(cards.chunked(2).size) { rowIndex ->
                val row = cards.chunked(2)[rowIndex]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { card ->
                        ToolCard(
                            card = card,
                            modifier = Modifier.weight(1f),
                            onClick = { card.route?.let(onNavigate) },
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item { QuickSummaryCard(snapshot, health) }
            state.error?.let { item { ErrorCard(it) } }
        }
    }
}

private fun buildToolboxCard(
    definition: ToolDefinition,
    title: String,
    state: DashboardUiState,
    shadowDisplaySubtitle: String,
    shadowDisplayBadge: String,
    agentSessionSubtitle: String,
    agentSessionBadge: String,
    adGovernanceSubtitle: String,
    adGovernanceBadge: String,
): ToolboxCard {
    val snapshot = state.snapshot
    val health = state.health
    val missingCapabilities = definition.requiredCapabilities.filterNot { capabilityAvailable(it, state) }
    val status = when (definition.id) {
        ToolId.DASHBOARD -> (
            if (health.rootAvailable) "CPU %.0f%% · Mem %.1f GB".format(health.cpuUsagePercent, health.memory.availableKb / 1_048_576f)
            else "CPU · 内存 · ZRAM · 温控"
        ) to (health.thermal.apC?.let { "%.0f°C".format(it) } ?: "LIVE")
        ToolId.PERFORMANCE -> "${state.mode.displayName} · ${snapshot.thermalStage().displayName}" to (snapshot.apTempC?.let { "%.0f°C".format(it) } ?: "CPU")
        ToolId.SHADOW_DISPLAY -> shadowDisplaySubtitle to shadowDisplayBadge
        ToolId.AGENT_SESSION -> agentSessionSubtitle to agentSessionBadge
        ToolId.ROOT_ADB -> (
            when {
                state.adb.rootTcpEnabled -> "${state.adb.tailscaleIpv4 ?: state.adb.localIpv4 ?: "TCP"}:${state.adb.rootTcpPort ?: 5555}"
                state.adb.nativeWirelessEnabled -> "Android Wireless Debugging 已开启"
                else -> "Root TCP · Wireless · USB"
            }
        ) to when {
            state.adb.rootTcpEnabled -> "TCP"
            state.adb.nativeWirelessEnabled -> "WIFI"
            else -> "OFF"
        }
        ToolId.ROOT_TAILSCALE -> (
            state.network.tailscaleIpv4?.let { "$it · Root overlay" }
                ?: "Root overlay · Hiddify coexistence"
        ) to if (state.network.tailscaleIpv4 != null) "TAILNET" else "ROOT"
        ToolId.PERMISSIONS -> when {
            snapshot.rootAvailable && state.notificationsGranted -> "所需权限已就绪" to "ROOT"
            snapshot.rootAvailable -> "Root 已授权" to "CHECK"
            else -> "自动申请 Root 权限" to "CHECK"
        }
        ToolId.STARTUP -> (
            if (state.startup.apps.isNotEmpty()) "本次启动 ${state.startup.startedApps} App · ${state.startup.bootCapableApps} Boot"
            else "开机时间线 · Receiver · 启动排名"
        ) to if (state.startup.apps.isNotEmpty()) state.startup.startedApps.toString() else "SCAN"
        ToolId.APPS -> (
            if (state.appInventory.apps.isNotEmpty()) "${state.appInventory.apps.size} apps · ${state.appInventory.runningApps} running · ${state.appInventory.frozenApps} frozen"
            else "Inventory · Components · Permissions · AppOps"
        ) to if (state.appInventory.apps.isNotEmpty()) "${state.appInventory.apps.size}" else "MANAGE"
        ToolId.DIAGNOSTICS -> (
            if (state.diagnostics.topProcesses.isNotEmpty()) "Root shell ${state.diagnostics.abnormalRootShells} abnormal · ${state.diagnostics.services.size} services"
            else "Top CPU · Root Shell · WakeLock"
        ) to if (state.diagnostics.topProcesses.isNotEmpty()) "SCAN" else "CHECK"
        ToolId.MODULES -> (
            if (state.modules.magiskModules.isNotEmpty() || state.modules.vectorModules.isNotEmpty()) "Magisk ${state.modules.enabledMagiskCount} · Vector ${state.modules.enabledVectorCount} enabled"
            else "Magisk · Vector · Xposed"
        ) to if (state.modules.vectorActive) "VECTOR" else "CHECK"
        ToolId.ACTIONS -> (
            if (state.favoriteActions.isNotEmpty()) "${state.favoriteActions.size} favorites · Automation · Report"
            else "SystemUI · adbd · Automation · Report"
        ) to if (state.favoriteActions.isNotEmpty()) state.favoriteActions.size.toString() else "TOOLS"
        ToolId.NETWORK -> (
            if (state.network.interfaces.isNotEmpty()) "${state.network.primarySummary} · ${state.network.tailscaleIpv4 ?: "No Tailscale"}"
            else "Interfaces · Route · DNS · Ports"
        ) to if (state.network.tailscaleActive) "TAILNET" else "NET"
        ToolId.STORAGE -> (
            state.storage.primary?.let { "${"%.0f".format(it.availableKb / 1_048_576f)} GB free · IO PSI ${"%.2f".format(state.storage.ioPressure.someAvg10)}" }
                ?: "Capacity · IO PSI · UFS"
        ) to (state.storage.primary?.status?.displayName ?: "DISK")
        ToolId.BATTERY -> (
            health.thermal.skinC?.let { skin -> "Skin ${"%.0f".format(skin)}° · Battery ${health.thermal.batteryC?.let { "%.0f".format(it) } ?: "—"}° · T${health.thermal.status}" }
                ?: "Battery · Charging · Thermal"
        ) to if (health.battery.protectionEnabled) "80%" else "BAT"
        ToolId.SHIZUKU -> when {
            state.shizuku.ready -> "${state.shizuku.backend.displayName} · UID ${state.shizuku.uid ?: -1} · Ready" to when (state.shizuku.backend) {
                com.arthur.roottools.model.PrivilegeBackendType.SUI_ROOT -> "SUI"
                com.arthur.roottools.model.PrivilegeBackendType.SHIZUKU_ROOT -> "ROOT"
                com.arthur.roottools.model.PrivilegeBackendType.SHIZUKU_ADB -> "ADB"
                else -> "READY"
            }
            state.shizuku.binderAlive -> "Shizuku Binder 在线 · 等待授权" to "AUTH"
            state.shizuku.managerInstalled || state.shizuku.suiAvailable -> "服务未连接 · 打开 Shizuku / Sui" to "OFF"
            else -> "未检测到 Shizuku / Sui" to "OFF"
        }
        ToolId.COMPONENTS -> state.componentSnapshot?.let { component ->
            "${component.label} · ${component.components.size} components · ${component.disabledCount} disabled" to
                if (component.systemApp) "READ" else "EDIT"
        } ?: ("Activity · Service · Receiver · Provider" to "COMP")
        ToolId.PERMISSION_OPS -> state.permissionOpsSnapshot?.let { info ->
            "${info.label} · ${info.grantedPermissions}/${info.permissions.size} permissions · ${info.appOps.count { it.supported }} AppOps" to
                if (info.appOpsBackendAvailable) "EDIT" else "READ"
        } ?: ("Runtime Permission · AppOps · Special Access" to "PERM")
        ToolId.INTEGRITY -> "Fast · Deep · Native · Attestation" to "SCAN"
        ToolId.ASSISTANT -> "Default assistant · power button" to "ASSIST"
        ToolId.DEVELOPER_RUNTIME -> "Termux · CLI · Managed Tasks" to "DEV"
        ToolId.AD_GOVERNANCE -> adGovernanceSubtitle to adGovernanceBadge
    }
    val setupTool = definition.id == ToolId.PERMISSIONS || definition.id == ToolId.SHIZUKU
    val subtitle = if (missingCapabilities.isNotEmpty() && !setupTool) {
        "缺 ${missingCapabilities.joinToString { it.name }} · ${status.first}"
    } else status.first
    val badge = if (missingCapabilities.isNotEmpty() && !setupTool) "SETUP" else status.second
    return ToolboxCard(
        title = title,
        subtitle = subtitle,
        icon = definition.icon,
        accent = definition.accent,
        route = routeFor(definition.id),
        badge = badge,
    )
}

@Composable
private fun ToolboxHero(snapshot: DeviceSnapshot, mode: PerformanceMode) {
    val stage = snapshot.thermalStage()
    val gradient = when (stage) {
        ThermalStage.NORMAL -> listOf(Color(0xFF153C31), Color(0xFF101A1E))
        ThermalStage.WARM -> listOf(Color(0xFF4A3819), Color(0xFF1E1914))
        ThermalStage.MODERATE -> listOf(Color(0xFF5B301C), Color(0xFF201816))
        ThermalStage.SEVERE -> listOf(Color(0xFF5E2326), Color(0xFF211416))
    }
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(gradient)).padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Dashboard, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("设备状态良好", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${mode.displayName} · ${stage.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RootBadge(snapshot.rootAvailable)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TempMetric("AP", snapshot.apTempC, Modifier.weight(1f))
                    TempMetric("机身", snapshot.skinTempC, Modifier.weight(1f))
                    TempMetric("电池", snapshot.batteryTempC, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ToolCard(card: ToolboxCard, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val enabled = card.route != null
    Card(
        modifier = modifier
            .height(148.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = card.accent.copy(alpha = if (enabled) 0.14f else 0.08f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(card.icon, null, tint = card.accent.copy(alpha = if (enabled) 1f else 0.48f))
                    }
                }
                Spacer(Modifier.weight(1f))
                card.badge?.let {
                    Surface(color = card.accent.copy(alpha = 0.10f), shape = RoundedCornerShape(50)) {
                        Text(
                            it,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = card.accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Column {
                Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        card.subtitle,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.58f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (enabled) Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun QuickSummaryCard(snapshot: DeviceSnapshot, health: DeviceHealthSnapshot) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)),
    ) {
        Column(Modifier.padding(17.dp)) {
            Text("快速状态", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(11.dp))
            SummaryRow("Root", if (snapshot.rootAvailable) "已授权" else "待授权")
            SummaryRow("ADB", if (snapshot.adbEnabled) "${snapshot.tailscaleIpv4 ?: "TCP"}:${snapshot.adbPort ?: 5555}" else "关闭")
            SummaryRow("Thermal", "${snapshot.thermalStage().displayName} · status ${snapshot.thermalStatus}")
            if (health.rootAvailable) {
                SummaryRow("CPU", "%.0f%% · load %.2f".format(health.cpuUsagePercent, health.load1))
                SummaryRow("Memory", "%.1f GB available · %s".format(health.memory.availableKb / 1_048_576f, health.memory.status.displayName))
            }
        }
    }
}

@Composable
internal fun AdbScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAdbToggle: () -> Unit,
    onNativeWirelessToggle: (Boolean) -> Unit,
    onRootBootRestore: (Boolean) -> Unit,
    onNativeBootRestore: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val adb = state.adb
    var confirmDisable by remember { mutableStateOf(false) }
    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("关闭 Root ADB？") },
            text = {
                Text("当前管理链路可能正通过 ${adb.tailscaleIpv4 ?: "网络"}:${adb.rootTcpPort ?: 5555} 连接。关闭 Root TCP 后，这次远程 ADB 会话可能立即失联。")
            },
            confirmButton = {
                TextButton(onClick = { confirmDisable = false; onAdbToggle() }) { Text("仍然关闭") }
            },
            dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text("取消") } },
        )
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { DetailHeader("ADB Control Center", "Root TCP · Android Wireless · USB", onBack, state.adbLoading, onRefresh) }
            item {
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                Modifier.size(48.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.13f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.WifiTethering, null, tint = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("连接总览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    when {
                                        !adb.rootAvailable -> "等待 Root 权限"
                                        adb.rootTcpEnabled && adb.nativeWirelessEnabled -> "Root TCP 与 Wireless Debugging 均在线"
                                        adb.rootTcpEnabled -> "Root TCP 在线"
                                        adb.nativeWirelessEnabled -> "Wireless Debugging 在线"
                                        else -> "当前无线 ADB 未开启"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (adb.rootTcpEnabled || adb.nativeWirelessEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    if (adb.rootTcpEnabled || adb.nativeWirelessEnabled) "ONLINE" else "OFF",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (adb.rootTcpEnabled || adb.nativeWirelessEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                        AdbToggleRow(
                            title = "Root TCP ADB",
                            subtitle = if (adb.rootTcpEnabled) "监听 ${adb.rootTcpPort ?: 5555} · 适合 Tailscale / 远程管理" else "固定 5555 · 不依赖同一 Wi‑Fi",
                            checked = adb.rootTcpEnabled,
                            enabled = adb.rootAvailable && !state.actionInProgress,
                            onCheckedChange = {
                                if (adb.rootTcpEnabled) confirmDisable = true else onAdbToggle()
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        AdbToggleRow(
                            title = "Android Wireless Debugging",
                            subtitle = when {
                                !adb.nativeWirelessSupported -> "系统未报告支持"
                                adb.nativeWirelessEnabled && adb.nativeTlsPort != null -> "TLS 动态端口 ${adb.nativeTlsPort}"
                                adb.nativeWirelessEnabled -> "已开启 · 等待 Wi‑Fi / TLS listener"
                                else -> "Android 11+ 原生无线调试与配对体系"
                            },
                            checked = adb.nativeWirelessEnabled,
                            enabled = adb.rootAvailable && adb.nativeWirelessSupported && !state.actionInProgress,
                            onCheckedChange = onNativeWirelessToggle,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Terminal, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("USB Debugging", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (adb.usbTransportActive) "USB ADB transport 当前可用" else if (adb.usbDebuggingEnabled) "ADB 已允许，当前未检测到 USB ADB transport" else "系统 ADB Debugging 未开启",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(if (adb.usbTransportActive) "ACTIVE" else if (adb.usbDebuggingEnabled) "READY" else "OFF", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item { SectionLabel("Connection Endpoints", "优先使用 Tailscale；局域网和 Native TLS 作为补充") }
            if (adb.endpoints.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("暂无可连接 Endpoint", fontWeight = FontWeight.SemiBold)
                            Text("开启 Root TCP 或 Android Wireless Debugging 后，这里会显示可以复制和分享的连接地址。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                itemsIndexed(adb.endpoints) { index, endpoint ->
                    AdbEndpointCard(
                        endpoint = endpoint,
                        onCopy = { copyToClipboard(context, endpoint.connectCommand) },
                        onShare = { shareText(context, "Root Tools ADB", endpoint.connectCommand) },
                    )
                }
            }

            item { SectionLabel("Pairing & Trusted Devices", "只展示受信任主机名称，不在 UI 暴露 ADB 公钥") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Security, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("系统配对", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Wireless ${if (adb.nativeWirelessSupported) "supported" else "unsupported"} · QR ${if (adb.nativeWirelessQrSupported) "supported" else "unsupported"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = {
                                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                            }) { Text("开发者选项") }
                        }
                        HorizontalDivider()
                        if (adb.trustedHosts.isEmpty()) {
                            Text("暂未读取到已授权主机。新设备配对仍建议在系统 Wireless Debugging 页面完成。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            adb.trustedHosts.forEach { host ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(host, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text("TRUSTED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            item { SectionLabel("Startup & Persistence", "只在你显式开启时恢复；不做常驻 1 秒轮询") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AdbToggleRow(
                            title = "重启后恢复 Root TCP 5555",
                            subtitle = "BOOT_COMPLETED / USER_UNLOCKED 触发；失败只做有限次数重试",
                            checked = adb.bootPolicy.restoreRootTcp,
                            enabled = adb.rootAvailable,
                            onCheckedChange = onRootBootRestore,
                        )
                        HorizontalDivider()
                        AdbToggleRow(
                            title = "重启后恢复 Wireless Debugging",
                            subtitle = "恢复 adb_wifi_enabled；实际 TLS listener 仍取决于系统和 Wi‑Fi",
                            checked = adb.bootPolicy.restoreNativeWireless,
                            enabled = adb.rootAvailable && adb.nativeWirelessSupported,
                            onCheckedChange = onNativeBootRestore,
                        )
                    }
                }
            }

            item { SectionLabel("Quick Entry", "通知栏 Tile + 桌面 Widget，共用同一 AdbController") }
            item { QuickTileCard() }
            item {
                Surface(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.07f), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Launcher Widget", fontWeight = FontWeight.SemiBold)
                            Text("桌面添加 Root Tools 的 2×1 ADB Widget：关闭时可一键开启 Root TCP；已开启时点击进入 Control Center。状态按事件刷新，不做高频 Root polling。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            state.actionMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            state.error?.let { item { ErrorCard(it) } }
            item {
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.055f), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Security, null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(9.dp))
                        Text("Root TCP 默认不做公网暴露；远程连接优先走 Tailscale。Quick Tile、Widget 与 Automation 只允许“确保开启”，关闭动作只保留在本页并要求确认。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdbToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AdbEndpointCard(
    endpoint: AdbEndpoint,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (endpoint.type == AdbEndpointType.TAILSCALE) Icons.Rounded.Security else Icons.Rounded.SettingsEthernet,
                    null,
                    tint = if (endpoint.recommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(endpoint.type.displayName, fontWeight = FontWeight.SemiBold)
                        if (endpoint.recommended) {
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                                Text("推荐", modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Text(endpoint.address, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("复制 adb connect")
                }
                OutlinedButton(onClick = onShare) {
                    Icon(Icons.Rounded.Share, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("分享")
                }
            }
        }
    }
}

@Composable
internal fun StartupScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val analysis = state.startup
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DetailHeader(
                    title = "启动治理",
                    subtitle = "分析本次 boot 的真实 am_proc_start，不增加 Boot Receiver",
                    onBack = onBack,
                    loading = state.startupLoading,
                    onRefresh = onRefresh,
                )
            }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HealthMetric("Started", analysis.startedApps.toString(), "third-party apps", Modifier.weight(1f))
                            HealthMetric("Boot", analysis.bootCapableApps.toString(), "receiver capable", Modifier.weight(1f))
                            HealthMetric("Running", analysis.runningApps.toString(), "now", Modifier.weight(1f))
                        }
                        Text("当前 uptime：${formatUptime(analysis.bootUptimeSeconds)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { SectionLabel("启动时间线", "首次进程启动分桶") }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        analysis.buckets.forEach { bucket ->
                            SummaryRow(bucket.label, "${bucket.appCount} apps · ${bucket.processStarts} starts")
                        }
                    }
                }
            }
            item { SectionLabel("启动排名", "综合启动次数、Boot Receiver 与当前常驻状态") }
            if (analysis.apps.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Text("正在读取本次启动事件…", modifier = Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                itemsIndexed(analysis.apps.take(24)) { index, app ->
                    StartupAppCard(index + 1, app)
                }
            }
            state.error?.let { item { ErrorCard(it) } }
        }
    }
}

@Composable
private fun StartupAppCard(rank: Int, app: StartupAppRecord) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = CircleShape) {
                    Text("$rank", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PolicyBadge(app.category)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("first ${formatStartupSeconds(app.firstStartSeconds)}", style = MaterialTheme.typography.labelMedium)
                Text("${app.startCount} starts", style = MaterialTheme.typography.labelMedium)
                Text("${app.bootReceiverCount} boot", style = MaterialTheme.typography.labelMedium)
                if (app.running) Text("RUNNING", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (app.startReasons.isNotEmpty()) {
                Text(app.startReasons.take(3).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AppsGovernanceScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onFreeze: (String) -> Unit,
    onEnable: (String) -> Unit,
    onForceStop: (String) -> Unit,
    onBucket: (String, Int) -> Unit,
    onBackground: (String, Boolean) -> Unit,
    onAppiumMode: (Boolean) -> Unit,
) {
    var pendingFreeze by remember { mutableStateOf<StartupAppRecord?>(null) }
    val writeAvailable = state.snapshot.rootAvailable || state.shizuku.ready
    val candidates = state.startup.apps
        .filter { state.startup.degradedMode || it.category != AppPolicyCategory.NORMAL || it.disabled || it.running || it.bootReceiverCount > 0 }
        .sortedWith(compareBy<StartupAppRecord> { categoryOrder(it.category) }.thenByDescending { it.startupRiskScore })
        .take(if (state.startup.degradedMode) 100 else 40)

    pendingFreeze?.let { app ->
        AlertDialog(
            onDismissRequest = { pendingFreeze = null },
            title = { Text("冻结 ${app.label}？") },
            text = { Text("会执行 disable-user，应用将无法响应开机广播和后台服务。之后可以在本页重新启用。") },
            confirmButton = {
                TextButton(onClick = { pendingFreeze = null; onFreeze(app.packageName) }) { Text("冻结") }
            },
            dismissButton = { TextButton(onClick = { pendingFreeze = null }) { Text("取消") } },
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DetailHeader(
                    title = "应用治理",
                    subtitle = "${state.startup.source} · Freeze · Standby · AppOps",
                    onBack = onBack,
                    loading = state.startupLoading || state.actionInProgress,
                    onRefresh = onRefresh,
                )
            }
            if (state.startup.degradedMode) {
                item {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(16.dp)) {
                        Text(
                            "当前是 Framework/Shizuku 治理模式：应用目录、运行/禁用、Bucket、BOOT Receiver 可用；没有 Root 的 am_proc_start 时间线不会伪造。",
                            Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Appium 测试模式", fontWeight = FontWeight.Bold)
                            Text("Notification Listener + Doze whitelist 按需启用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.startup.appiumTestMode, onCheckedChange = onAppiumMode, enabled = writeAvailable && !state.actionInProgress)
                    }
                }
            }
            state.actionMessage?.let {
                item {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                        Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            item { SectionLabel("建议治理对象", "保护名单不会提供冻结入口") }
            if (candidates.isEmpty()) {
                item { Text("正在读取应用策略…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                itemsIndexed(candidates) { _, app ->
                    AppPolicyCard(
                        app = app,
                        busy = state.actionInProgress || !writeAvailable,
                        onFreeze = { pendingFreeze = app },
                        onEnable = { onEnable(app.packageName) },
                        onForceStop = { onForceStop(app.packageName) },
                        onRare = { onBucket(app.packageName, 40) },
                        onRestricted = { onBucket(app.packageName, 45) },
                        onAllowBackground = { onBackground(app.packageName, true) },
                        onIgnoreBackground = { onBackground(app.packageName, false) },
                    )
                }
            }
            state.error?.let { item { ErrorCard(it) } }
        }
    }
}

@Composable
private fun AppPolicyCard(
    app: StartupAppRecord,
    busy: Boolean,
    onFreeze: () -> Unit,
    onEnable: () -> Unit,
    onForceStop: () -> Unit,
    onRare: () -> Unit,
    onRestricted: () -> Unit,
    onAllowBackground: () -> Unit,
    onIgnoreBackground: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PolicyBadge(app.category)
            }
            Text(
                "${if (app.running) "Running" else "Stopped"} · ${if (app.disabled) "Frozen" else "Enabled"} · Bucket ${app.standbyBucket ?: "—"} · Boot ${app.bootReceiverCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (app.disabled) {
                    Button(onClick = onEnable, enabled = !busy) { Text("Enable") }
                } else if (app.category != AppPolicyCategory.PROTECTED) {
                    OutlinedButton(onClick = onRare, enabled = !busy) { Text("Rare") }
                    OutlinedButton(onClick = onRestricted, enabled = !busy) { Text("Restrict") }
                    Button(onClick = onFreeze, enabled = !busy) { Text("Freeze") }
                } else {
                    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), shape = RoundedCornerShape(12.dp)) {
                        Text("Protected", Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (!app.disabled && app.category != AppPolicyCategory.PROTECTED) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TextButton(onClick = onForceStop, enabled = !busy) { Text("Force stop") }
                    TextButton(onClick = onAllowBackground, enabled = !busy) { Text("BG allow") }
                    TextButton(onClick = onIgnoreBackground, enabled = !busy) { Text("BG ignore") }
                }
            }
        }
    }
}

@Composable
private fun PolicyBadge(category: AppPolicyCategory) {
    val tint = when (category) {
        AppPolicyCategory.PROTECTED -> Color(0xFF79D9B0)
        AppPolicyCategory.FREEZE -> Color(0xFFFF9B8E)
        AppPolicyCategory.ON_DEMAND -> Color(0xFFFFC56F)
        AppPolicyCategory.RARE -> Color(0xFFAFC2FF)
        AppPolicyCategory.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(category.displayName, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = tint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun DiagnosticsScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAttributeRootShell: (Int) -> Unit,
) {
    val context = LocalContext.current
    val diagnostics = state.diagnostics
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DetailHeader(
                    title = "进程诊断",
                    subtitle = "按需采集 · 深度 FD 扫描只在点击时运行",
                    onBack = onBack,
                    loading = state.diagnosticsLoading || state.actionInProgress,
                    onRefresh = onRefresh,
                )
            }
            item { LagForensicsCard() }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val top = diagnostics.topProcesses.firstOrNull()
                        HealthMetric("Top CPU", top?.let { "%.0f%%".format(it.cpuPercent) } ?: "—", top?.processName ?: "waiting", Modifier.weight(1f))
                        HealthMetric("Root Shell", diagnostics.abnormalRootShells.toString(), "abnormal", Modifier.weight(1f))
                        HealthMetric("WakeLock", diagnostics.wakeLocks.activeCount.toString(), "active", Modifier.weight(1f))
                    }
                }
            }
            item { SectionLabel("Top Processes", "单次 top 快照，不持续轮询") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (diagnostics.topProcesses.isEmpty()) {
                            Text("等待诊断采样…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else diagnostics.topProcesses.take(12).forEach { process ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(process.processName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                    Text("PID ${process.pid} · PPID ${process.ppid} · ${process.user} · RSS ${process.rss}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("%.1f%%".format(process.cpuPercent), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Root Shell", "高 CPU 时可进一步匹配共享 pipe owner") }
            if (diagnostics.rootShells.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Text("当前没有检测到 Root shell。", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                itemsIndexed(diagnostics.rootShells) { _, shell ->
                    RootShellCard(
                        shell = shell,
                        details = state.rootShellDetails[shell.pid],
                        busy = state.actionInProgress,
                        onAttribute = { onAttributeRootShell(shell.pid) },
                    )
                }
            }
            item { SectionLabel("WakeLock", "当前持有与最近 release attribution") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Active ${diagnostics.wakeLocks.activeCount}", fontWeight = FontWeight.Bold)
                        diagnostics.wakeLocks.activeLines.take(6).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (diagnostics.wakeLocks.recentLines.isNotEmpty()) {
                            HorizontalDivider()
                            Text("Recent", style = MaterialTheme.typography.labelLarge)
                            diagnostics.wakeLocks.recentLines.take(6).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
            item { SectionLabel("Active Services", "仅展示第三方服务") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (diagnostics.services.isEmpty()) Text("没有读取到第三方 Active Service", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        diagnostics.services.take(24).forEach { service ->
                            Row(Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(service.packageName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(service.component, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (service.foreground) Text("FGS", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Diagnostic Snapshot", fontWeight = FontWeight.Bold)
                            Text("当前设备状态 + Top + Root Shell + WakeLock + Service", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { if (state.diagnosticText.isNotBlank()) copyToClipboard(context, state.diagnosticText) },
                            enabled = state.diagnosticText.isNotBlank(),
                        ) { Text("复制") }
                    }
                }
            }
            state.actionMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            state.error?.let { item { ErrorCard(it) } }
        }
    }
}

@Composable
private fun RootShellCard(
    shell: RootShellRecord,
    details: com.arthur.roottools.model.RootShellDetails?,
    busy: Boolean,
    onAttribute: () -> Unit,
) {
    val abnormal = shell.cpuPercent >= 50f
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (abnormal) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PID ${shell.pid} · PPID ${shell.ppid}", fontWeight = FontWeight.Bold)
                    Text("${shell.command} · ${shell.elapsed}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("%.1f%%".format(shell.cpuPercent), color = if (abnormal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onAttribute, enabled = !busy) { Text("分析 pipe 归属") }
            details?.let { detail ->
                Text("fd: ${detail.fd0} / ${detail.fd1} / ${detail.fd2}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("syscr ${detail.syscr} · rchar ${detail.rchar} · read ${detail.readBytes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                detail.attributions.take(8).forEach { owner ->
                    Text("${owner.pipe} → PID ${owner.ownerPid} fd${owner.ownerFd} · ${owner.ownerCommand}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun ModuleCenterScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onMagiskToggle: (String, Boolean) -> Unit,
    onVectorToggle: (String, Boolean) -> Unit,
    onLoadScope: (String) -> Unit,
) {
    var pendingMagisk by remember { mutableStateOf<Pair<MagiskModuleInfo, Boolean>?>(null) }
    var pendingVector by remember { mutableStateOf<Pair<VectorModuleInfo, Boolean>?>(null) }

    pendingMagisk?.let { (module, enabled) ->
        AlertDialog(
            onDismissRequest = { pendingMagisk = null },
            title = { Text("${if (enabled) "启用" else "禁用"} ${module.name}？") },
            text = { Text("Magisk / Zygisk 模块状态会在下次重启生效。Root Tools 不会自动重启设备。") },
            confirmButton = {
                TextButton(onClick = { pendingMagisk = null; onMagiskToggle(module.id, enabled) }) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { pendingMagisk = null }) { Text("取消") } },
        )
    }
    pendingVector?.let { (module, enabled) ->
        AlertDialog(
            onDismissRequest = { pendingVector = null },
            title = { Text("${if (enabled) "启用" else "停用"} Vector 模块？") },
            text = { Text(module.packageName) },
            confirmButton = {
                TextButton(onClick = { pendingVector = null; onVectorToggle(module.packageName, enabled) }) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { pendingVector = null }) { Text("取消") } },
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DetailHeader(
                    title = "Root 模块",
                    subtitle = "Magisk / Zygisk · Vector / Xposed",
                    onBack = onBack,
                    loading = state.modulesLoading || state.actionInProgress,
                    onRefresh = onRefresh,
                )
            }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HealthMetric("Magisk", state.modules.enabledMagiskCount.toString(), "enabled", Modifier.weight(1f))
                        HealthMetric("Vector", state.modules.enabledVectorCount.toString(), "Xposed modules", Modifier.weight(1f))
                        HealthMetric("Daemon", if (state.modules.vectorActive) "ON" else "OFF", "vectord", Modifier.weight(1f))
                    }
                }
            }
            if (state.pendingRebootModules.isNotEmpty()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(16.dp)) {
                        Text(
                            "等待重启生效：${state.pendingRebootModules.joinToString()}",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
            item { SectionLabel("Magisk Modules", "模块 marker 修改后不自动 reboot") }
            if (state.modules.magiskModules.isEmpty()) {
                item { Text("正在读取 Magisk modules…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                itemsIndexed(state.modules.magiskModules) { _, module ->
                    MagiskModuleCard(
                        module = module,
                        pendingReboot = module.id in state.pendingRebootModules,
                        busy = state.actionInProgress,
                        onToggle = { enabled -> pendingMagisk = module to enabled },
                    )
                }
            }
            item { SectionLabel("Vector / Xposed", "Scope 按需读取，不做后台轮询") }
            if (state.modules.vectorModules.isEmpty()) {
                item { Text("没有读取到 Vector modules", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                itemsIndexed(state.modules.vectorModules) { _, module ->
                    VectorModuleCard(
                        module = module,
                        scope = state.modules.scopes[module.packageName],
                        busy = state.actionInProgress,
                        onToggle = { enabled -> pendingVector = module to enabled },
                        onLoadScope = { onLoadScope(module.packageName) },
                    )
                }
            }
            state.actionMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            state.error?.let { item { ErrorCard(it) } }
        }
    }
}

@Composable
private fun MagiskModuleCard(
    module: MagiskModuleInfo,
    pendingReboot: Boolean,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val enabled = !module.disabledMarker && !module.removeMarker
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(module.name, fontWeight = FontWeight.SemiBold)
                    Text("${module.id} · ${module.version}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    color = (if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(if (enabled) "ENABLED" else "DISABLED", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (module.description.isNotBlank()) Text(module.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    module.protected -> Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), shape = RoundedCornerShape(12.dp)) {
                        Text("Protected framework", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.primary)
                    }
                    else -> OutlinedButton(onClick = { onToggle(!enabled) }, enabled = !busy) { Text(if (enabled) "下次重启禁用" else "下次重启启用") }
                }
                if (pendingReboot) Text("Pending reboot", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun VectorModuleCard(
    module: VectorModuleInfo,
    scope: List<com.arthur.roottools.model.VectorScopeEntry>?,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onLoadScope: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(module.packageName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("UID ${module.uid}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = module.enabled, onCheckedChange = { onToggle(it) }, enabled = !busy)
            }
            OutlinedButton(onClick = onLoadScope, enabled = !busy) { Text(if (scope == null) "读取 Scope" else "刷新 Scope") }
            scope?.let { entries ->
                if (entries.isEmpty()) Text("Scope 为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else entries.take(12).forEach { entry ->
                    Text("${entry.packageName} / user ${entry.userId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun CommonActionsScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onRunAction: (SystemActionId) -> Unit,
    onModeSelected: (PerformanceMode) -> Unit,
    onExportDiagnostic: () -> Unit,
    onFavorite: (SystemActionId, Boolean) -> Unit,
) {
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<SystemActionId?>(null) }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(action.displayName) },
            text = { Text(action.description) },
            confirmButton = {
                TextButton(onClick = { pendingAction = null; onRunAction(action) }) { Text("执行") }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("取消") } },
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DetailHeader(
                    title = "常用操作",
                    subtitle = "固定语义动作 · 当前不开放远程重启入口",
                    onBack = onBack,
                    loading = state.actionInProgress,
                    onRefresh = {},
                )
            }
            item { SectionLabel("快速入口", "直接打开已有系统 / Root 工具") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        QuickOpenRow("Developer Options", "Android 开发者设置") {
                            runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                        }
                        QuickOpenRow("Magisk", "超级用户 / Modules") { openPackage(context, "com.topjohnwu.magisk") }
                        QuickOpenRow("Vector", "Xposed module / scope") { openPackage(context, "org.matrix.vector.manager") }
                        QuickOpenRow("Hail", "冻结应用") { openPackage(context, "com.aistra.hail") }
                        QuickOpenRow("MacroDroid", "App 白名单与事件编排") { openPackage(context, "com.arlosoft.macrodroid") }
                    }
                }
            }
            item { SectionLabel("性能快捷操作", "统一走 CPU Policy Controller") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onModeSelected(PerformanceMode.AUTO) }, modifier = Modifier.weight(1f)) { Text("Auto") }
                    OutlinedButton(onClick = { onModeSelected(PerformanceMode.COOL) }, modifier = Modifier.weight(1f)) { Text("Cool") }
                    OutlinedButton(onClick = { onModeSelected(PerformanceMode.PERFORMANCE) }, modifier = Modifier.weight(1f)) { Text("Performance") }
                }
            }
            item { SectionLabel("系统动作", "会修改系统状态，因此执行前再次确认") }
            val orderedActions = SystemActionId.entries.sortedByDescending { it in state.favoriteActions }
            itemsIndexed(orderedActions) { _, action ->
                ActionCard(
                    action = action,
                    busy = state.actionInProgress,
                    favorite = action in state.favoriteActions,
                    onFavorite = { favorite -> onFavorite(action, favorite) },
                    onClick = { pendingAction = action },
                )
            }
            item { SectionLabel("Root 写操作审计", "只记录真实系统修改；展示语义动作与回滚提示，不展示危险 shell") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.auditRecords.isEmpty()) {
                            Text("暂无 Root 写操作。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            state.auditRecords.take(20).forEachIndexed { index, record ->
                                if (index > 0) HorizontalDivider()
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    Box(
                                        Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape)
                                            .background(if (record.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("${record.feature} · ${record.action}", fontWeight = FontWeight.SemiBold)
                                        Text(
                                            listOf(record.source, record.target).filter { it.isNotBlank() }.joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (record.before.isNotBlank() || record.after.isNotBlank()) {
                                            Text(
                                                "${record.before.ifBlank { "—" }} → ${record.after.ifBlank { "—" }}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (record.rollbackHint.isNotBlank()) {
                                            Text("回滚：${record.rollbackHint}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                        }
                                    }
                                    Text(formatRelativeTime(record.timestampMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Automation API", "MacroDroid / ADB 使用本机 token 调用固定动作") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Local token", fontWeight = FontWeight.Bold)
                        Text(
                            if (state.automationToken.length > 12) "${state.automationToken.take(8)}••••${state.automationToken.takeLast(4)}" else state.automationToken,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = { copyToClipboard(context, state.automationToken) }) { Text("复制 token") }
                        Text("允许：SET_MODE / SET_ADB / SET_NATIVE_ADB / RUN_DIAGNOSTIC / FREEZE / UNFREEZE", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val performanceCommand = "adb shell am broadcast -n com.arthur.roottools/.automation.ActionRouterReceiver -a com.arthur.roottools.ACTION --es token ${state.automationToken} --es command SET_MODE --es mode PERFORMANCE"
                        val autoCommand = "adb shell am broadcast -n com.arthur.roottools/.automation.ActionRouterReceiver -a com.arthur.roottools.ACTION --es token ${state.automationToken} --es command SET_MODE --es mode AUTO"
                        Text("App 白名单：MacroDroid 在应用进入时切 Performance，退出时切 Auto；Root Tools 不长期监听前台 App。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { copyToClipboard(context, performanceCommand) }) { Text("复制 Performance") }
                            TextButton(onClick = { copyToClipboard(context, autoCommand) }) { Text("复制 Auto") }
                        }
                    }
                }
            }
            item { SectionLabel("诊断报告", "生成后可通过 Android Share Sheet 分享") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.lastReportPath ?: "尚未生成报告", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onExportDiagnostic, enabled = !state.actionInProgress) { Text("生成报告") }
                            state.lastReportPath?.let { path ->
                                OutlinedButton(onClick = { shareDiagnosticFile(context, path) }) { Text("分享") }
                            }
                        }
                    }
                }
            }
            state.actionMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            state.error?.let { item { ErrorCard(it) } }
        }
    }
}

@Composable
private fun QuickOpenRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun ActionCard(
    action: SystemActionId,
    busy: Boolean,
    favorite: Boolean,
    onFavorite: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFFB74D)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(action.displayName, fontWeight = FontWeight.SemiBold)
                Text(action.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onFavorite(!favorite) }, enabled = !busy) {
                Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, contentDescription = if (favorite) "取消收藏" else "收藏")
            }
            Text("CONFIRM", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun NetworkDiagnosticsScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPing: (String) -> Unit,
) {
    val network = state.network
    var target by rememberSaveable { mutableStateOf("") }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DetailHeader(
                    title = "网络诊断",
                    subtitle = "按需读取 · 不做持续 ping",
                    onBack = onBack,
                    loading = state.networkLoading || state.actionInProgress,
                    onRefresh = onRefresh,
                )
            }
            item { NetworkOverviewCard(network) }
            item { SectionLabel("Interfaces", "本机 IPv4 与角色") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (network.interfaces.isEmpty()) Text("等待网络快照…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        network.interfaces.forEach { info ->
                            SummaryRow(info.name, "${info.ipv4}/${info.prefixLength}")
                        }
                    }
                }
            }
            item { SectionLabel("Route / DNS", "Android 策略路由会与 VPN/Wi-Fi/Cellular 并存") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("DNS", fontWeight = FontWeight.Bold)
                        Text(network.dnsServers.joinToString(" · ").ifBlank { "—" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider()
                        Text("Routes", fontWeight = FontWeight.Bold)
                        network.routes.take(16).forEach { route ->
                            Text(route, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { SectionLabel("Listening TCP", "只展示监听 socket，不做端口扫描") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (network.listeningPorts.isEmpty()) Text("没有读取到 TCP listen socket", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        network.listeningPorts.take(24).forEach { port ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(port.address.ifBlank { "*" }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(port.port.toString(), fontWeight = FontWeight.Bold, color = if (port.port == 5555) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
            item { SectionLabel("单次连通性测试", "手工输入 hostname / IPv4；只执行 ping -c 3") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = target,
                            onValueChange = { target = it.take(253) },
                            label = { Text("Target") },
                            placeholder = { Text("100.x.x.x or host") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = { onPing(target) }, enabled = target.isNotBlank() && !state.actionInProgress) { Text("Ping 3 次") }
                        state.pingResult?.let { ping ->
                            Text(
                                if (ping.success) {
                                    "${ping.target} · ${ping.packetsReceived}/${ping.packetsTransmitted} · loss ${"%.0f".format(ping.packetLossPercent)}% · avg ${ping.avgMs?.let { "%.1f ms".format(it) } ?: "—"}"
                                } else {
                                    "${ping.target} · 连接失败"
                                },
                                color = if (ping.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            state.error?.let { item { ErrorCard(it) } }
        }
    }
}

@Composable
private fun NetworkOverviewCard(network: NetworkSnapshot) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthMetric("Transport", network.primarySummary, network.radioTechnology ?: "active", Modifier.weight(1f))
                HealthMetric("Tailscale", network.tailscaleIpv4 ?: "OFF", if (network.tailscaleActive) "VPN active" else "not active", Modifier.weight(1f))
                HealthMetric("ADB", if (network.adbListening) "${network.adbPort}" else "OFF", "TCP", Modifier.weight(1f))
            }
            network.carrierName?.let { SummaryRow("Carrier", it) }
            network.wifiIpv4?.let { SummaryRow("Wi-Fi", it) }
            network.cellularIpv4?.let { SummaryRow("Cellular", it) }
        }
    }
}

@Composable
internal fun StorageDiagnosticsScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val storage = state.storage
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DetailHeader(
                    title = "存储与 IO",
                    subtitle = "只读观测 · 不执行清理或磁盘 benchmark",
                    onBack = onBack,
                    loading = state.storageLoading,
                    onRefresh = onRefresh,
                )
            }
            item { StorageOverviewCard(storage) }
            item { SectionLabel("Filesystems", "路径视角，Samsung/FUSE 可能共享同一底层容量") }
            if (storage.fileSystems.isEmpty()) {
                item { Text("等待存储快照…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                itemsIndexed(storage.fileSystems) { _, fs ->
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(fs.label, fontWeight = FontWeight.Bold)
                                Text(fs.status.displayName, color = storageStatusColor(fs.status), fontWeight = FontWeight.SemiBold)
                            }
                            SummaryRow("Total", "%.1f GB".format(fs.totalKb / 1_048_576f))
                            SummaryRow("Used", "%.1f GB · ${fs.usedPercent}%".format(fs.usedKb / 1_048_576f))
                            SummaryRow("Available", "%.1f GB".format(fs.availableKb / 1_048_576f))
                            Text("${fs.filesystem} → ${fs.mountedOn}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item { SectionLabel("IO Pressure", "PSI 表示任务因 IO 等待而受阻的真实压力") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(storage.ioStatus.displayName, fontWeight = FontWeight.Bold, color = when (storage.ioStatus) {
                            MemoryPressureStatus.HEALTHY -> MaterialTheme.colorScheme.primary
                            MemoryPressureStatus.WATCH -> Color(0xFFFFB74D)
                            MemoryPressureStatus.PRESSURE -> MaterialTheme.colorScheme.error
                        })
                        SummaryRow("some avg10 / 60 / 300", "%.2f / %.2f / %.2f".format(storage.ioPressure.someAvg10, storage.ioPressure.someAvg60, storage.ioPressure.someAvg300))
                        SummaryRow("full avg10 / 60 / 300", "%.2f / %.2f / %.2f".format(storage.ioPressure.fullAvg10, storage.ioPressure.fullAvg60, storage.ioPressure.fullAvg300))
                    }
                }
            }
            item { SectionLabel("Physical Blocks", "累计量用于理解 I/O 活动，不作为健康度结论") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        if (storage.blockDevices.isEmpty()) Text("未读取到物理块设备", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        storage.blockDevices.take(8).forEach { block ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(block.name, fontWeight = FontWeight.SemiBold)
                                    Text("reads ${block.readsCompleted} · writes ${block.writesCompleted}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("R %.1f GB".format(block.readGb), style = MaterialTheme.typography.labelMedium)
                                    Text("W %.1f GB".format(block.writtenGb), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
            state.error?.let { item { ErrorCard(it) } }
        }
    }
}

@Composable
private fun StorageOverviewCard(storage: StorageSnapshot) {
    val primary = storage.primary
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HealthMetric("Free", primary?.let { "%.0f GB".format(it.availableKb / 1_048_576f) } ?: "—", primary?.status?.displayName ?: "capacity", Modifier.weight(1f))
            HealthMetric("Used", primary?.let { "${it.usedPercent}%" } ?: "—", primary?.let { "%.0f GB".format(it.usedKb / 1_048_576f) } ?: "data", Modifier.weight(1f))
            HealthMetric("IO PSI", "%.2f".format(storage.ioPressure.someAvg10), storage.ioStatus.displayName, Modifier.weight(1f))
        }
    }
}

@Composable
private fun storageStatusColor(status: StorageStatus): Color = when (status) {
    StorageStatus.HEALTHY -> MaterialTheme.colorScheme.primary
    StorageStatus.WATCH -> Color(0xFFFFB74D)
    StorageStatus.LOW_SPACE -> MaterialTheme.colorScheme.error
}

@Composable
internal fun BatteryThermalScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onProtectionToggle: (Boolean) -> Unit,
    onSamplingSeconds: (Int) -> Unit,
) {
    val health = state.health
    val history = state.healthHistory.takeLast(900)
    val apValues = history.mapNotNull { it.apTempC }
    val skinValues = history.mapNotNull { it.skinTempC }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DetailHeader(
                    title = "电池与温控",
                    subtitle = "复用设备采样器 · 不修改 Samsung Thermal",
                    onBack = onBack,
                    loading = state.actionInProgress,
                    onRefresh = {},
                )
            }
            item { SamplingIntervalCard(state.detailSamplingSeconds, onSamplingSeconds) }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HealthMetric("AP", health.thermal.apC?.let { "%.1f°".format(it) } ?: "—", "Thermal ${health.thermal.status}", Modifier.weight(1f))
                            HealthMetric("Skin", health.thermal.skinC?.let { "%.1f°".format(it) } ?: "—", health.thermalStageLabel(), Modifier.weight(1f))
                            HealthMetric("Battery", health.thermal.batteryC?.let { "%.1f°".format(it) } ?: "—", "${health.battery.level ?: 0}%", Modifier.weight(1f))
                        }
                        if (history.size >= 2) {
                            Text("30m thermal trend · AP / Skin", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            RootToolsTemperatureSparkline(history)
                        }
                    }
                }
            }
            item { SectionLabel("Charging", "电池实时电气状态") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryRow("Charging", if (health.battery.charging) "正在充电" else "未充电")
                        SummaryRow("Level", "${health.battery.level ?: 0}%")
                        SummaryRow("Voltage", "${health.battery.voltageMv ?: 0} mV")
                        SummaryRow("Current", "${health.battery.currentMa ?: 0} mA")
                        SummaryRow("USB", health.thermal.usbC?.let { "%.1f°C".format(it) } ?: "—")
                        SummaryRow("PATHM", health.thermal.pathmC?.let { "%.1f°C".format(it) } ?: "—")
                    }
                }
            }
            item { SectionLabel("Battery Protection", "长期插电测试机建议保持 80% 保护") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Samsung Battery Protection", fontWeight = FontWeight.Bold)
                            Text(
                                if (health.battery.protectionEnabled) "ON · ${health.battery.protectionThreshold ?: 80}%" else "OFF",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = health.battery.protectionEnabled,
                            onCheckedChange = onProtectionToggle,
                            enabled = health.rootAvailable && !state.actionInProgress,
                        )
                    }
                }
            }
            item { SectionLabel("Recent thermal range", "来自当前进程内环形历史") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryRow("AP min / max", rangeText(apValues))
                        SummaryRow("Skin min / max", rangeText(skinValues))
                        SummaryRow("Current policy", "${state.mode.displayName} · ${health.thermalStageLabel()}")
                        Text(
                            when {
                                health.thermal.status >= 2 -> "系统已经进入明显热限制；Root Tools 不会主动抬高 CPU 上限。"
                                health.thermal.status == 1 -> "轻度热状态：Auto 允许继续削峰，但不覆盖 Samsung 当前限频。"
                                else -> "Thermal 0：允许 Auto 在安全范围内恢复正常峰值策略。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { DailyHealthHistoryCard(state.dailyHealthHistory) }
            state.actionMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            state.error?.let { item { ErrorCard(it) } }
        }
    }
}

@Composable
internal fun PermissionScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRequestRoot: () -> Unit,
    onRequestShizuku: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { DetailHeader("权限中心", "首次启动会主动触发所需授权", onBack, state.loading, onRefresh) }
            item {
                PermissionItem(
                    title = "Root 权限",
                    description = "性能控制和 Root ADB 都通过 Magisk su 执行",
                    granted = state.snapshot.rootAvailable,
                    action = if (!state.snapshot.rootAvailable) "重新申请" else null,
                    onAction = onRequestRoot,
                )
            }
            item {
                PermissionItem(
                    title = "Shizuku / Sui",
                    description = when {
                        state.shizuku.ready -> "${state.shizuku.backend.displayName} · UID ${state.shizuku.uid} · Binder Ready"
                        state.shizuku.binderAlive -> "Binder 已连接，需要授权 Root Tools"
                        state.shizuku.managerInstalled || state.shizuku.suiAvailable -> "已安装但服务当前未连接"
                        else -> "未检测到 Shizuku / Sui；不影响 RootShell 功能"
                    },
                    granted = state.shizuku.ready,
                    action = if (state.shizuku.binderAlive && !state.shizuku.permissionGranted) "申请授权" else null,
                    onAction = onRequestShizuku,
                )
            }
            item {
                PermissionItem(
                    title = "通知权限",
                    description = "Auto / Performance 模式的低频温控守护需要前台通知",
                    granted = state.notificationsGranted,
                    action = null,
                    onAction = {},
                )
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        "Root Tools 会自动发起权限申请，但 Magisk Root、Android 通知权限等系统授权必须由你在系统弹窗中确认，应用不会绕过系统确认流程。",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { SectionLabel("工具可用性", "根据 ToolRegistry 的 Capability 自动计算") }
            itemsIndexed(ToolRegistry.tools) { _, definition ->
                val missing = definition.requiredCapabilities.filterNot { capabilityAvailable(it, state) }
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            color = definition.accent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(definition.icon, contentDescription = null, tint = definition.accent)
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(definition.titleRes), fontWeight = FontWeight.SemiBold)
                            Text(
                                if (definition.requiredCapabilities.isEmpty()) {
                                    "无需额外能力"
                                } else {
                                    definition.requiredCapabilities.joinToString(" · ") { it.name }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(
                            color = (if (missing.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error).copy(alpha = 0.10f),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                if (missing.isEmpty()) "READY" else "缺 ${missing.joinToString { it.name }}",
                                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (missing.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun capabilityAvailable(capability: ToolCapability, state: DashboardUiState): Boolean = when (capability) {
    ToolCapability.ROOT -> state.snapshot.rootAvailable
    ToolCapability.NOTIFICATION -> state.notificationsGranted
    ToolCapability.MAGISK -> state.modules.magiskModules.isNotEmpty()
    ToolCapability.VECTOR -> state.modules.vectorActive
    ToolCapability.NETWORK -> true // Network diagnostics can open offline and explain missing links.
    ToolCapability.SHIZUKU -> state.shizuku.ready
    ToolCapability.PACKAGE_CONTROL -> state.snapshot.rootAvailable || state.shizuku.ready
}

@Composable
internal fun ComponentManagerScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onLoad: (String) -> Unit,
    onSetEnabled: (AppComponentRecord, Boolean) -> Unit,
) {
    var packageName by rememberSaveable(state.componentSnapshot?.packageName) {
        mutableStateOf(state.componentSnapshot?.packageName.orEmpty())
    }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var pendingDisable by remember { mutableStateOf<AppComponentRecord?>(null) }
    val snapshot = state.componentSnapshot
    val editable = snapshot != null && !snapshot.systemApp && snapshot.packageName !in com.arthur.roottools.policy.ComponentPolicyController.PROTECTED_PACKAGES
    val filtered = snapshot?.components.orEmpty().filter { component ->
        when (filter) {
            "BOOT" -> component.bootReceiver
            "EXPORTED" -> component.exported
            "FGS" -> component.foregroundService
            "DISABLED" -> !component.enabled
            else -> true
        }
    }

    pendingDisable?.let { component ->
        AlertDialog(
            onDismissRequest = { pendingDisable = null },
            title = { Text("禁用 ${component.kind.displayName}？") },
            text = {
                Text("${component.className}\n\n禁用组件可能改变应用启动、推送或后台行为。操作会写入 Root 审计，可重新启用恢复。")
            },
            confirmButton = {
                TextButton(onClick = { pendingDisable = null; onSetEnabled(component, false) }) { Text("禁用") }
            },
            dismissButton = { TextButton(onClick = { pendingDisable = null }) { Text("取消") } },
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DetailHeader(
                    title = "组件管理",
                    subtitle = "PackageManager 读取 · Shizuku/RootShell 写入",
                    onBack = onBack,
                    loading = state.componentLoading || state.actionInProgress,
                    onRefresh = { if (packageName.isNotBlank()) onLoad(packageName) },
                )
            }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = packageName,
                            onValueChange = { packageName = it.take(180) },
                            label = { Text("Package name") },
                            placeholder = { Text("com.example.app") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { onLoad(packageName) },
                            enabled = packageName.isNotBlank() && !state.componentLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("读取组件") }
                        if (state.componentCatalog.isNotEmpty()) {
                            Text("常用已安装应用", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            state.componentCatalog.take(6).forEach { (pkg, label) ->
                                TextButton(onClick = { packageName = pkg; onLoad(pkg) }) {
                                    Text("$label · $pkg", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            snapshot?.let { app ->
                item {
                    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(
                                    color = (if (editable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(50),
                                ) {
                                    Text(if (editable) "EDITABLE" else "READ ONLY", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            SummaryRow("Components", app.components.size.toString())
                            SummaryRow("BOOT receivers", app.bootReceiverCount.toString())
                            SummaryRow("Exported", app.exportedCount.toString())
                            SummaryRow("Foreground services", app.foregroundServiceCount.toString())
                            SummaryRow("Disabled", app.disabledCount.toString())
                            if (app.systemApp) {
                                Text("系统应用仅允许检查，不提供组件写操作。", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        items(5) { index ->
                            val value = listOf("ALL", "BOOT", "EXPORTED", "FGS", "DISABLED")[index]
                            FilterChip(
                                selected = filter == value,
                                onClick = { filter = value },
                                label = { Text(value) },
                            )
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item { Text("当前筛选没有组件。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    itemsIndexed(filtered.take(160)) { _, component ->
                        ComponentManagerRow(
                            component = component,
                            editable = editable && component.protectedReason == null && !state.actionInProgress,
                            onEnable = { onSetEnabled(component, true) },
                            onDisable = { pendingDisable = component },
                        )
                    }
                }
            }
            state.actionMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            state.error?.let { item { ErrorCard(it) } }
        }
    }
}

@Composable
private fun ComponentManagerRow(
    component: AppComponentRecord,
    editable: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(component.kind.displayName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(component.className, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                }
                Switch(
                    checked = component.enabled,
                    enabled = editable,
                    onCheckedChange = { enabled -> if (enabled) onEnable() else onDisable() },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (component.bootReceiver) SmallStatusPill("BOOT", Color(0xFFFFC56F))
                if (component.exported) SmallStatusPill("EXPORTED", Color(0xFFAFC2FF))
                if (component.foregroundService) SmallStatusPill("FGS", Color(0xFFA9F5D0))
                if (component.directBootAware) SmallStatusPill("DIRECT BOOT", Color(0xFF9ED8C8))
                component.protectedReason?.let { SmallStatusPill("CORE", Color(0xFFFFC56F)) }
                if (!component.enabled) SmallStatusPill("DISABLED", MaterialTheme.colorScheme.error)
            }
            component.protectedReason?.let {
                Text("protected: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
            component.permission?.takeIf { it.isNotBlank() }?.let {
                Text("permission: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SmallStatusPill(label: String, tint: Color) {
    Surface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun PermissionAppOpsScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onLoad: (String) -> Unit,
    onSetMode: (String, String) -> Unit,
) {
    var packageName by rememberSaveable(state.permissionOpsSnapshot?.packageName) {
        mutableStateOf(state.permissionOpsSnapshot?.packageName.orEmpty())
    }
    val snapshot = state.permissionOpsSnapshot
    val canWrite = state.snapshot.rootAvailable || state.shizuku.ready
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DetailHeader(
                    title = "权限 / AppOps",
                    subtitle = "PackageManager 读取 · 固定 AppOps 白名单写入",
                    onBack = onBack,
                    loading = state.permissionOpsLoading || state.actionInProgress,
                    onRefresh = { if (packageName.isNotBlank()) onLoad(packageName) },
                )
            }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = packageName,
                            onValueChange = { packageName = it.take(180) },
                            label = { Text("Package name") },
                            placeholder = { Text("com.example.app") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { onLoad(packageName) },
                            enabled = packageName.isNotBlank() && !state.permissionOpsLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("读取权限 / AppOps") }
                        state.componentCatalog.take(6).forEach { (pkg, label) ->
                            TextButton(onClick = { packageName = pkg; onLoad(pkg) }) {
                                Text("$label · $pkg", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            snapshot?.let { info ->
                item {
                    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(info.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(info.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SummaryRow("Runtime permissions", "${info.grantedPermissions} granted / ${info.deniedPermissions} denied")
                            SummaryRow("AppOps backend", if (canWrite) {
                                if (state.shizuku.ready) state.shizuku.backend.displayName else "RootShell"
                            } else "Unavailable · read-only")
                            if (!canWrite) {
                                Text("当前没有 Root 或 Shizuku/Sui backend；Runtime Permission 仍可查看，AppOps 不执行。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
                item { SectionLabel("Runtime Permissions", "requested permissions · 只读") }
                if (info.permissions.isEmpty()) {
                    item { Text("该应用没有声明 runtime permission。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    itemsIndexed(info.permissions.take(120)) { _, permission ->
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(permission.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                    Text(permission.protection, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                SmallStatusPill(
                                    if (permission.granted) "GRANTED" else "DENIED",
                                    if (permission.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
                item { SectionLabel("AppOps", "固定白名单；unsupported 会明确显示") }
                if (!info.appOpsBackendAvailable) {
                    item { Text("无可用特权 backend，因此本次没有读取 AppOps。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    itemsIndexed(info.appOps) { _, op ->
                        AppOpCard(op, editable = canWrite && !state.actionInProgress, onSetMode = { mode -> onSetMode(op.name, mode) })
                    }
                }
            }
            state.actionMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            state.error?.let { item { ErrorCard(it) } }
        }
    }
}

@Composable
private fun AppOpCard(op: AppOpRecord, editable: Boolean, onSetMode: (String) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(op.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (op.supported) "${op.backend.displayName} · ${op.mode ?: "mode unknown"}" else "Unsupported / unavailable",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SmallStatusPill(op.mode?.uppercase() ?: "N/A", if (op.supported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            if (op.raw.isNotBlank()) {
                Text(op.raw, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (op.supported) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val modes = listOf("allow", "ignore", "default", "deny", "foreground")
                    items(modes.size) { index ->
                        val mode = modes[index]
                        FilterChip(
                            selected = op.mode == mode,
                            enabled = editable,
                            onClick = { onSetMode(mode) },
                            label = { Text(mode) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ShizukuSuiScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onSelfTest: () -> Unit,
) {
    val context = LocalContext.current
    val bridge = state.shizuku
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DetailHeader(
                    title = "Shizuku / Sui",
                    subtitle = "Framework privilege bridge · RootShell 互补后端",
                    onBack = onBack,
                    loading = false,
                    onRefresh = onRefresh,
                )
            }
            item {
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HealthMetric("Binder", if (bridge.binderAlive) "ON" else "OFF", if (bridge.binderAlive) "alive" else "disconnected", Modifier.weight(1f))
                            HealthMetric("Backend", bridge.backend.displayName, "UID ${bridge.uid ?: "—"}", Modifier.weight(1f))
                            HealthMetric("Auth", if (bridge.permissionGranted) "Ready" else "No", if (bridge.suiAvailable) "Sui available" else "Shizuku", Modifier.weight(1f))
                        }
                        SummaryRow("Server API", bridge.serverVersion?.toString() ?: "—")
                        SummaryRow("Patch", bridge.serverPatchVersion?.toString() ?: "—")
                        SummaryRow("SELinux", bridge.selinuxContext ?: "—")
                        SummaryRow("Manager", if (bridge.managerInstalled) "Installed" else "Not detected")
                        SummaryRow("Sui", if (bridge.suiAvailable) "Available" else "Not detected")
                        bridge.lastBinderDeathAt?.let { SummaryRow("Last disconnect", formatRelativeTime(it)) }
                        bridge.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (bridge.binderAlive && !bridge.permissionGranted) {
                item {
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !bridge.permissionDeniedPermanently,
                    ) { Text(if (bridge.permissionDeniedPermanently) "需要在 Manager 中重新授权" else "授权 Root Tools 使用 Shizuku") }
                }
            }
            item { SectionLabel("快速入口", "Shizuku Manager 负责服务本身，Root Tools 只消费 API") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        QuickOpenRow("Shizuku Manager", "启动 / 授权 / 服务状态") { openPackage(context, com.arthur.roottools.privilege.ShizukuBridge.SHIZUKU_MANAGER) }
                        QuickOpenRow("Wireless Debugging", "Android 11+ 无线调试设置") {
                            runCatching { context.startActivity(Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS")) }
                        }
                        QuickOpenRow("Developer Options", "开发者选项") {
                            runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                        }
                    }
                }
            }
            item { SectionLabel("职责边界", "Framework API 走 Shizuku；Linux root / sysfs 继续 RootShell") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        SummaryRow("应用 / 组件 / AppOps", if (bridge.ready) bridge.backend.displayName else "RootShell fallback")
                        SummaryRow("CPU / Thermal sysfs", "RootShell")
                        SummaryRow("Root ADB", "RootShell")
                        SummaryRow("Magisk / Vector", "RootShell")
                        Text("下一层能力通过 typed gateway 路由，不向 UI 暴露任意 Shizuku/rish 命令。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { SectionLabel("Capability Self-test", "固定只读测试 · 不执行任意命令或状态修改") }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Button(
                            onClick = onSelfTest,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = bridge.ready && !state.shizukuSelfTestLoading,
                        ) {
                            if (state.shizukuSelfTestLoading) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("运行 Self-test")
                        }
                        if (state.shizukuSelfTest.isEmpty()) {
                            Text("验证 Binder / UserService UID / Package / Activity / AppOps 与调用延迟。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            state.shizukuSelfTest.forEachIndexed { index, probe ->
                                if (index > 0) HorizontalDivider()
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    Box(
                                        Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape)
                                            .background(if (probe.available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(probe.capability.name, fontWeight = FontWeight.SemiBold)
                                        Text("${probe.backend.displayName} · ${probe.detail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    probe.latencyMs?.let { Text("%.1f ms".format(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                }
            }
            state.actionMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            state.error?.let { item { ErrorCard(it) } }
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    description: String,
    granted: Boolean,
    action: String?,
    onAction: () -> Unit,
) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = (if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary).copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.Security,
                        null,
                        tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (action != null) {
                OutlinedButton(onClick = onAction) { Text(action) }
            } else {
                Text(
                    if (granted) "已授权" else "待确认",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun Header(model: String, rootAvailable: Boolean, loading: Boolean, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Root Tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("$model · Personal Root Toolbox", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRefresh, enabled = !loading) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
        }
    }
}

@Composable
private fun RootBadge(rootAvailable: Boolean) {
    val tint = if (rootAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Surface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.width(6.dp))
            Text(if (rootAvailable) "ROOT" else "NO ROOT", style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HeroCard(snapshot: DeviceSnapshot, mode: PerformanceMode) {
    val stage = snapshot.thermalStage()
    val colors = when (stage) {
        ThermalStage.NORMAL -> listOf(Color(0xFF163A30), Color(0xFF121F24))
        ThermalStage.WARM -> listOf(Color(0xFF4B3A1A), Color(0xFF201C16))
        ThermalStage.MODERATE -> listOf(Color(0xFF5A301D), Color(0xFF231B18))
        ThermalStage.SEVERE -> listOf(Color(0xFF5D2225), Color(0xFF251617))
    }
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(colors)).padding(20.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Thermostat, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("${mode.displayName} · ${stage.displayName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TempMetric("AP", snapshot.apTempC, Modifier.weight(1f))
                    TempMetric("机身", snapshot.skinTempC, Modifier.weight(1f))
                    TempMetric("电池", snapshot.batteryTempC, Modifier.weight(1f))
                }
                Spacer(Modifier.height(15.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(snapshot.thermalStatus == 0)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            snapshot.thermalStatus == 0 -> "系统温控正常，无热节流"
                            snapshot.thermalStatus == 1 -> "轻度热状态，Auto 只会继续削峰"
                            else -> "系统正在热保护，Root Tools 不会抬高频率"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TempMetric(label: String, value: Float?, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color.White.copy(alpha = 0.055f), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value?.let { "%.1f°".format(it) } ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusDot(ok: Boolean) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary))
}

@Composable
private fun PerformanceModeCard(
    mode: PerformanceMode,
    stage: ThermalStage,
    busy: Boolean,
    onModeSelected: (PerformanceMode) -> Unit,
) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("性能档位", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Performance 默认 15 分钟，系统热保护优先", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PerformanceMode.entries.forEach { item ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = mode == item,
                        enabled = !busy,
                        onClick = { onModeSelected(item) },
                        label = { Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            AnimatedContent(targetState = mode, label = "modeDescription") { target ->
                Text(
                    when (target) {
                        PerformanceMode.AUTO -> "自动依据 Thermal / 机身温度切换内部阶段。当前：${stage.displayName}。"
                        PerformanceMode.COOL -> "长期低温档，主要削减性能核与超大核的高功耗频率尾段。"
                        PerformanceMode.PERFORMANCE -> "短时峰值档；15 分钟后回 Auto，Thermal > 0 时不会覆盖系统限频。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CpuClusterCard(
    cluster: CpuCluster,
    label: String,
    capState: com.arthur.roottools.model.CpuCapState? = null,
) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("CPU ${cluster.relatedCpus.ifBlank { cluster.policyId.toString() }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                capState?.let { state ->
                    Text(
                        "限频来源：${state.source.displayName}${if (state.ownedMaxKHz > 0) " · owned ${formatGHz(state.ownedMaxKHz)}" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = when (state.source) {
                            com.arthur.roottools.model.CpuCapSource.ROOT_TOOLS -> MaterialTheme.colorScheme.primary
                            com.arthur.roottools.model.CpuCapSource.SAMSUNG_THERMAL -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatGHz(cluster.currentKHz), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("上限 ${formatGHz(cluster.scalingMaxKHz)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AdbCard(
    snapshot: DeviceSnapshot,
    busy: Boolean,
    onToggle: () -> Unit,
    onCopy: (String) -> Unit,
    onDeveloperSettings: () -> Unit,
) {
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(46.dp), shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.WifiTethering, null, tint = MaterialTheme.colorScheme.secondary) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Root ADB over TCP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("固定 5555 · Tailscale / 5G", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = snapshot.adbEnabled, enabled = snapshot.rootAvailable && !busy, onCheckedChange = { onToggle() })
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            Spacer(Modifier.height(14.dp))
            val address = snapshot.tailscaleIpv4?.let { "$it:${snapshot.adbPort ?: 5555}" }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Terminal, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("远程地址", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(address ?: if (snapshot.adbEnabled) "ADB 已开，未识别 Tailscale IP" else "开启后显示 Tailscale 地址", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                if (address != null) {
                    IconButton(onClick = { onCopy("adb connect $address") }) { Icon(Icons.Rounded.ContentCopy, contentDescription = "复制连接命令") }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDeveloperSettings, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.SettingsEthernet, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("开发者选项")
                }
                if (address != null) {
                    Button(onClick = { onCopy("adb connect $address") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("复制 ADB")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Root ADB 不依赖 Android 原生无线调试的 Wi‑Fi 限制。默认不做永久开机开放，减少长期暴露风险。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickTileCard() {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("通知栏快捷入口", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("编辑快捷面板，可添加“CPU 档位”“Root ADB”和“Wireless ADB”。Root ADB 只负责确保 5555 已开启；Wireless ADB 可切换 Android 原生无线调试。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SafetyCard() {
    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.055f), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Security, null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(9.dp))
            Text("不关闭 Samsung Thermal、不绑核、不替换 governor；Thermal > 0 时 Root Tools 只允许继续削峰，不会抬高系统当前限制。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
