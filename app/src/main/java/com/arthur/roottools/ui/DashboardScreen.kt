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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import com.arthur.roottools.model.CpuCluster
import com.arthur.roottools.model.AppPolicyCategory
import com.arthur.roottools.model.AppComponentRecord
import com.arthur.roottools.model.ComponentKind
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
import com.arthur.roottools.model.StartupDataSource
import com.arthur.roottools.model.StorageSnapshot
import com.arthur.roottools.model.StorageStatus
import com.arthur.roottools.model.SystemActionId
import com.arthur.roottools.model.ThermalStage
import com.arthur.roottools.model.VectorModuleInfo
import com.arthur.roottools.policy.ComponentSafetyPolicy
import java.io.File
import kotlin.math.roundToInt

private enum class ToolboxRoute {
    HOME,
    DASHBOARD,
    PERFORMANCE,
    ADB,
    PERMISSIONS,
    STARTUP,
    APPS,
    DIAGNOSTICS,
    MODULES,
    ACTIONS,
    NETWORK,
    STORAGE,
    BATTERY,
    SHIZUKU,
    COMPONENTS,
}

private enum class ComponentViewFilter(val displayName: String) {
    ALL("全部"),
    BOOT("BOOT"),
    EXPORTED("Exported"),
    FGS("FGS"),
    DISABLED("已停用"),
}

private data class ToolboxCard(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val route: ToolboxRoute?,
    val badge: String? = null,
)

@Composable
fun DashboardRoute(viewModel: DashboardViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf(ToolboxRoute.HOME) }

    LaunchedEffect(route) {
        viewModel.setDashboardSampling(route == ToolboxRoute.DASHBOARD || route == ToolboxRoute.BATTERY)
        if (route == ToolboxRoute.PERFORMANCE) viewModel.loadPerformanceExplain()
        if (route == ToolboxRoute.PERMISSIONS) viewModel.loadModules()
        if (route == ToolboxRoute.STARTUP) viewModel.loadStartup()
        if (route == ToolboxRoute.APPS) viewModel.loadApps()
        if (route == ToolboxRoute.DIAGNOSTICS) viewModel.loadDiagnostics()
        if (route == ToolboxRoute.MODULES) viewModel.loadModules()
        if (route == ToolboxRoute.ACTIONS) viewModel.loadAudit()
        if (route == ToolboxRoute.NETWORK) viewModel.loadNetwork()
        if (route == ToolboxRoute.STORAGE) viewModel.loadStorage()
        if (route == ToolboxRoute.COMPONENTS) viewModel.loadComponentCatalog()
    }

    BackHandler(enabled = route != ToolboxRoute.HOME) {
        if (route == ToolboxRoute.COMPONENTS && state.componentSnapshot != null) {
            viewModel.closeComponents()
        } else {
            route = ToolboxRoute.HOME
        }
    }

    when (route) {
        ToolboxRoute.HOME -> ToolboxHomeScreen(
            state = state,
            onRefresh = viewModel::refresh,
            onNavigate = { route = it },
        )
        ToolboxRoute.DASHBOARD -> HealthDashboardScreen(
            state = state,
            onBack = { route = ToolboxRoute.HOME },
            onRefresh = viewModel::refresh,
            onSamplingSeconds = viewModel::setDetailSamplingSeconds,
        )
        ToolboxRoute.PERFORMANCE -> PerformanceScreen(
            state = state,
            onBack = { route = ToolboxRoute.HOME },
            onRefresh = viewModel::refresh,
            onModeSelected = viewModel::setMode,
            onReleaseCaps = viewModel::releaseRootToolsCpuCaps,
        )
        ToolboxRoute.ADB -> AdbScreen(
            state = state,
            onBack = { route = ToolboxRoute.HOME },
            onRefresh = viewModel::refresh,
            onAdbToggle = viewModel::toggleAdb,
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
        ToolboxRoute.APPS -> AppsGovernanceScreen(
            state = state,
            onBack = { route = ToolboxRoute.HOME },
            onRefresh = viewModel::loadApps,
            onFreeze = viewModel::freezePackage,
            onEnable = viewModel::enablePackage,
            onForceStop = viewModel::forceStopPackage,
            onBucket = viewModel::setPackageBucket,
            onBackground = viewModel::setPackageBackground,
            onAppiumMode = viewModel::setAppiumTestMode,
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
            onRefreshCatalog = viewModel::loadComponentCatalog,
            onSelectPackage = viewModel::loadComponents,
            onClosePackage = viewModel::closeComponents,
            onSetEnabled = viewModel::setComponentEnabled,
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
    val cards = ToolRegistry.tools.map { definition -> buildToolboxCard(definition, state) }

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

private fun buildToolboxCard(definition: ToolDefinition, state: DashboardUiState): ToolboxCard {
    val snapshot = state.snapshot
    val health = state.health
    val missingCapabilities = definition.requiredCapabilities.filterNot { capabilityAvailable(it, state) }
    val status = when (definition.id) {
        ToolId.DASHBOARD -> (
            if (health.rootAvailable) "CPU %.0f%% · Mem %.1f GB".format(health.cpuUsagePercent, health.memory.availableKb / 1_048_576f)
            else "CPU · 内存 · ZRAM · 温控"
        ) to (health.thermal.apC?.let { "%.0f°C".format(it) } ?: "LIVE")
        ToolId.PERFORMANCE -> "${state.mode.displayName} · ${snapshot.thermalStage().displayName}" to (snapshot.apTempC?.let { "%.0f°C".format(it) } ?: "CPU")
        ToolId.ROOT_ADB -> (
            if (snapshot.adbEnabled) "${snapshot.tailscaleIpv4 ?: "TCP"}:${snapshot.adbPort ?: 5555}" else "一键开启 5555"
        ) to if (snapshot.adbEnabled) "ON" else "OFF"
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
            if (state.startup.apps.isNotEmpty()) "${state.startup.frozenApps} frozen · ${state.startup.runningApps} running"
            else "Freeze · Standby · AppOps · Shizuku"
        ) to if (state.startup.dataSource == StartupDataSource.FRAMEWORK_CATALOG) "SHIZUKU" else if (state.startup.apps.isNotEmpty()) "LIVE" else "MANAGE"
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
        ToolId.COMPONENTS -> (
            state.componentSnapshot?.let { "${it.label} · ${it.components.size} components · ${it.disabledCount} disabled" }
                ?: if (state.componentCatalog.isNotEmpty()) "${state.componentCatalog.size} user apps · Activity / Service / Receiver / Provider"
                else "BOOT · Exported · FGS · enable/disable"
        ) to if (state.shizuku.ready) state.shizuku.backend.displayName.replace("Shizuku ", "") else "TOOLS"
    }
    val subtitle = if (missingCapabilities.isNotEmpty()) {
        "需要 ${missingCapabilities.joinToString(" / ") { capabilityLabel(it) }} · ${status.first}"
    } else status.first
    val badge = if (missingCapabilities.isNotEmpty()) "SETUP" else status.second
    return ToolboxCard(
        title = definition.title,
        subtitle = subtitle,
        icon = definition.icon,
        accent = definition.accent,
        route = if (
            missingCapabilities.isEmpty() ||
            definition.id == ToolId.SHIZUKU ||
            definition.id == ToolId.PERMISSIONS
        ) routeFor(definition.id) else null,
        badge = badge,
    )
}

private fun routeFor(id: ToolId): ToolboxRoute = when (id) {
    ToolId.DASHBOARD -> ToolboxRoute.DASHBOARD
    ToolId.PERFORMANCE -> ToolboxRoute.PERFORMANCE
    ToolId.ROOT_ADB -> ToolboxRoute.ADB
    ToolId.PERMISSIONS -> ToolboxRoute.PERMISSIONS
    ToolId.STARTUP -> ToolboxRoute.STARTUP
    ToolId.APPS -> ToolboxRoute.APPS
    ToolId.DIAGNOSTICS -> ToolboxRoute.DIAGNOSTICS
    ToolId.MODULES -> ToolboxRoute.MODULES
    ToolId.ACTIONS -> ToolboxRoute.ACTIONS
    ToolId.NETWORK -> ToolboxRoute.NETWORK
    ToolId.STORAGE -> ToolboxRoute.STORAGE
    ToolId.BATTERY -> ToolboxRoute.BATTERY
    ToolId.SHIZUKU -> ToolboxRoute.SHIZUKU
    ToolId.COMPONENTS -> ToolboxRoute.COMPONENTS
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
private fun HealthDashboardScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSamplingSeconds: (Int) -> Unit,
) {
    val health = state.health
    val history = state.healthHistory
    val dailyHistory = state.dailyHealthHistory
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DetailHeader(
                    title = "设备看板",
                    subtitle = "${state.detailSamplingSeconds} 秒轻量采样 · Top Process 每 10 秒",
                    onBack = onBack,
                    loading = state.loading,
                    onRefresh = onRefresh,
                )
            }
            item { SamplingIntervalCard(state.detailSamplingSeconds, onSamplingSeconds) }
            item { HealthOverviewCard(health, history) }
            item { DailyHealthHistoryCard(dailyHistory) }
            item { SectionLabel("CPU / Load", "WALT 调度状态与每簇实时频率") }
            item { CpuHealthCard(health) }
            item { SchedulerHealthCard(health) }
            item { FrequencyDistributionCard(health, history) }
            item { SectionLabel("Memory / ZRAM", "以 MemAvailable + PSI 判断真实压力") }
            item { MemoryHealthCard(health) }
            item { TopMemoryCard(health) }
            item { LmkHealthCard(health) }
            item { SectionLabel("Thermal / Battery", "不关闭 Samsung Thermal，只观测状态") }
            item { ThermalBatteryCard(health) }
            item { SectionLabel("Process / System", "Top CPU 每 10 秒低频采样") }
            item { ProcessHealthCard(health) }
            if (!health.rootAvailable) {
                item { ErrorCard("看板等待 Root 数据。请先在权限中心完成 Magisk 授权。") }
            }
        }
    }
}

@Composable
private fun HealthOverviewCard(health: DeviceHealthSnapshot, history: List<HealthHistoryPoint>) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HealthMetric("CPU", "%.0f%%".format(health.cpuUsagePercent), "idle %.0f%%".format(health.cpuIdlePercent), Modifier.weight(1f))
                HealthMetric("Memory", "%.1f GB".format(health.memory.availableKb / 1_048_576f), "available", Modifier.weight(1f))
                HealthMetric("AP", health.thermal.apC?.let { "%.1f°".format(it) } ?: "—", "Thermal ${health.thermal.status}", Modifier.weight(1f))
            }
            if (history.size >= 2) {
                Text("最近状态", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HistorySparkline(history)
            }
            val abnormal = health.abnormalRootShell
            if (abnormal != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                    Text(
                        "检测到 Root Shell 高 CPU：PID ${abnormal.pid} · %.0f%%".format(abnormal.cpuPercent),
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthMetric(label: String, value: String, note: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistorySparkline(history: List<HealthHistoryPoint>, maxPoints: Int = 90) {
    val points = history.takeLast(maxPoints)
    val tint = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)) {
        if (points.size < 2) return@Canvas
        val max = 100f
        val step = size.width / (points.size - 1).coerceAtLeast(1)
        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(index * step, size.height * (1f - start.cpuUsagePercent / max)),
                end = androidx.compose.ui.geometry.Offset((index + 1) * step, size.height * (1f - end.cpuUsagePercent / max)),
                strokeWidth = 5f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun SamplingIntervalCard(selectedSeconds: Int, onSelected: (Int) -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("详情采样间隔", fontWeight = FontWeight.Bold)
                    Text("首页固定 30 秒；Top Process 最快仍为 10 秒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${selectedSeconds}s", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 5).forEach { seconds ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = selectedSeconds == seconds,
                        onClick = { onSelected(seconds) },
                        label = { Text(if (seconds == 1) "1s 实验" else "${seconds}s") },
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyHealthHistoryCard(history: List<HealthHistoryPoint>) {
    val ap = history.mapNotNull { it.apTempC }
    val skin = history.mapNotNull { it.skinTempC }
    val batteries = history.mapNotNull { it.batteryLevel }
    val minMem = history.minOfOrNull { it.memoryAvailableKb }
    val peakCpu = history.maxOfOrNull { it.cpuUsagePercent }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("24h 轻量历史", fontWeight = FontWeight.Bold)
                    Text("每 5 分钟最多 1 点 · 最多 288 点", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${history.size}/288", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            if (history.size >= 2) {
                HistorySparkline(history, maxPoints = 288)
                SummaryRow("CPU peak", peakCpu?.let { "%.0f%%".format(it) } ?: "—")
                SummaryRow("MemAvailable min", minMem?.let { "%.1f GB".format(it / 1_048_576f) } ?: "—")
                SummaryRow("AP min / max", rangeText(ap))
                SummaryRow("Skin min / max", rangeText(skin))
                SummaryRow(
                    "Battery min / max",
                    if (batteries.isEmpty()) "—" else "${batteries.minOrNull()}% / ${batteries.maxOrNull()}%",
                )
            } else {
                Text("持久历史将在采样满 5 分钟后开始累积；应用升级不会清空该文件。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TemperatureSparkline(history: List<HealthHistoryPoint>) {
    val cutoff = System.currentTimeMillis() - 30 * 60_000L
    val points = history.filter { it.timestampMs >= cutoff && (it.apTempC != null || it.skinTempC != null) }
    val apColor = MaterialTheme.colorScheme.tertiary
    val skinColor = MaterialTheme.colorScheme.primary
    val values = points.flatMap { listOfNotNull(it.apTempC, it.skinTempC) }
    val minValue = (values.minOrNull() ?: 25f) - 1f
    val maxValue = (values.maxOrNull() ?: 45f) + 1f
    val range = (maxValue - minValue).coerceAtLeast(1f)
    Canvas(modifier = Modifier.fillMaxWidth().height(88.dp)) {
        if (points.size < 2) return@Canvas
        val step = size.width / (points.size - 1).coerceAtLeast(1)
        fun y(value: Float) = size.height * (1f - (value - minValue) / range)
        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            if (start.apTempC != null && end.apTempC != null) {
                drawLine(
                    color = apColor,
                    start = androidx.compose.ui.geometry.Offset(index * step, y(start.apTempC)),
                    end = androidx.compose.ui.geometry.Offset((index + 1) * step, y(end.apTempC)),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
            }
            if (start.skinTempC != null && end.skinTempC != null) {
                drawLine(
                    color = skinColor,
                    start = androidx.compose.ui.geometry.Offset(index * step, y(start.skinTempC)),
                    end = androidx.compose.ui.geometry.Offset((index + 1) * step, y(end.skinTempC)),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun CpuHealthCard(health: DeviceHealthSnapshot) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("CPU %.0f%%".format(health.cpuUsagePercent), fontWeight = FontWeight.Bold)
                Text("Load %.2f / %.2f / %.2f".format(health.load1, health.load5, health.load15), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            health.cpuClusters.forEachIndexed { index, cluster ->
                if (index > 0) HorizontalDivider()
                val title = when {
                    index == 0 -> "能效核"
                    index == health.cpuClusters.lastIndex && index >= 2 -> "超大核"
                    else -> "性能核"
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("$title · CPU ${cluster.relatedCpus}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${cluster.governor.ifBlank { "—" }} · util ${"%.0f".format(cluster.utilizationPercent)}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatGHz(cluster.currentKHz), fontWeight = FontWeight.Bold)
                        Text("max ${formatGHz(cluster.scalingMaxKHz)} / hw ${formatGHz(cluster.hardwareMaxKHz)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SchedulerHealthCard(health: DeviceHealthSnapshot) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Scheduler / uclamp", fontWeight = FontWeight.Bold)
            if (health.scheduler.groups.isEmpty()) {
                Text("等待 scheduler 快照…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                health.scheduler.groups.forEachIndexed { index, group ->
                    if (index > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(group.name, fontWeight = FontWeight.SemiBold)
                            Text("CPU ${group.cpus.ifBlank { "—" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            when {
                                group.uclampMin != null || group.uclampMax != null -> "${group.uclampMin ?: "—"} → ${group.uclampMax ?: "—"}"
                                else -> "cpuset"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                "这里只读系统调度状态；Root Tools 不在看板页修改 cpuset / uclamp。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FrequencyDistributionCard(health: DeviceHealthSnapshot, history: List<HealthHistoryPoint>) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("Frequency distribution", fontWeight = FontWeight.Bold)
            Text("相对 hardware max：<35% / 35~60% / 60~80% / >80%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (history.none { it.clusterCurrentKHz.isNotEmpty() }) {
                Text("等待频率历史…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                health.cpuClusters.forEachIndexed { index, cluster ->
                    val name = when {
                        index == 0 -> "Efficiency"
                        index == health.cpuClusters.lastIndex && index >= 2 -> "Prime"
                        else -> "Big"
                    }
                    val five = frequencyBins(history, cluster.policyId, cluster.hardwareMaxKHz, 5 * 60_000L)
                    val thirty = frequencyBins(history, cluster.policyId, cluster.hardwareMaxKHz, 30 * 60_000L)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("$name · CPU ${cluster.relatedCpus}", fontWeight = FontWeight.SemiBold)
                        Text("5m   ${five.asLabel()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("30m ${thirty.asLabel()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryHealthCard(health: DeviceHealthSnapshot) {
    val memory = health.memory
    val statusColor = when (memory.status) {
        MemoryPressureStatus.HEALTHY -> MaterialTheme.colorScheme.primary
        MemoryPressureStatus.WATCH -> Color(0xFFFFB74D)
        MemoryPressureStatus.PRESSURE -> MaterialTheme.colorScheme.error
    }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${memory.status.displayName} · %.1f GB available".format(memory.availableKb / 1_048_576f), fontWeight = FontWeight.Bold)
                Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor))
            }
            SummaryRow("MemTotal", "%.1f GB".format(memory.totalKb / 1_048_576f))
            SummaryRow("Cached / Anon", "%.1f / %.1f GB".format(memory.cachedKb / 1_048_576f, memory.anonKb / 1_048_576f))
            SummaryRow("Swap", "%.1f / %.1f GB".format(memory.swapUsedKb / 1_048_576f, memory.swapTotalKb / 1_048_576f))
            SummaryRow("ZRAM compression", if (memory.compressionRatio > 0f) "%.2fx".format(memory.compressionRatio) else "—")
            SummaryRow("Memory PSI", "some %.2f · full %.2f".format(memory.pressure.someAvg10, memory.pressure.fullAvg10))
            SummaryRow("IO PSI", "some %.2f · full %.2f".format(health.ioPressure.someAvg10, health.ioPressure.fullAvg10))
            if (memory.swapUsedRatio >= 0.8f && memory.status == MemoryPressureStatus.HEALTHY) {
                Text(
                    "Swap 使用率高，但 MemAvailable 与 PSI 正常：当前不属于真实内存压力。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TopMemoryCard(health: DeviceHealthSnapshot) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Top RSS / PSS", fontWeight = FontWeight.Bold)
            Text("只对 RSS Top 6 读取 smaps_rollup", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (health.topMemoryProcesses.isEmpty()) {
                Text("等待下一次 10 秒内存进程采样…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                health.topMemoryProcesses.forEach { process ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(process.processName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text("PID ${process.pid} · ${process.user}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("RSS ${formatMemoryKb(process.rssKb)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text("PSS ${formatMemoryKb(process.pssKb)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LmkHealthCard(health: DeviceHealthSnapshot) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("LMKD / low memory", fontWeight = FontWeight.Bold)
                Text("${health.lmk.chimeraKillCount} LMKD", color = if (health.lmk.chimeraKillCount > 0) Color(0xFFFFB74D) else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            SummaryRow("Recent am_kill", health.lmk.recentKillCount.toString())
            if (health.lmk.config.isNotEmpty()) {
                health.lmk.config.entries.take(6).forEach { (key, value) -> SummaryRow(key, value) }
            } else {
                Text("设备未暴露 ro.lmk.* 静态参数；以 MemAvailable + PSI + kill 事件判断压力。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            health.lmk.recentReasons.takeLast(3).forEach { reason ->
                Text(reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ThermalBatteryCard(health: DeviceHealthSnapshot) {
    val thermal = health.thermal
    val battery = health.battery
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthMetric("AP", thermal.apC?.let { "%.1f°".format(it) } ?: "—", "status ${thermal.status}", Modifier.weight(1f))
                HealthMetric("Skin", thermal.skinC?.let { "%.1f°".format(it) } ?: "—", "surface", Modifier.weight(1f))
                HealthMetric("Battery", thermal.batteryC?.let { "%.1f°".format(it) } ?: "—", "${battery.level ?: 0}%", Modifier.weight(1f))
            }
            SummaryRow("USB / PATHM", "${thermal.usbC?.let { "%.1f°".format(it) } ?: "—"} / ${thermal.pathmC?.let { "%.1f°".format(it) } ?: "—"}")
            SummaryRow("Charging", if (battery.charging) "正在充电" else "未充电")
            SummaryRow("Voltage / Current", "${battery.voltageMv ?: 0} mV / ${battery.currentMa ?: 0} mA")
            SummaryRow("Battery protection", if (battery.protectionEnabled) "ON · ${battery.protectionThreshold ?: 80}%" else "OFF")
        }
    }
}

@Composable
private fun ProcessHealthCard(health: DeviceHealthSnapshot) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryRow("Uptime", formatUptime(health.uptimeSeconds))
            SummaryRow("Processes", health.processCount.toString())
            HorizontalDivider()
            if (health.topProcesses.isEmpty()) {
                Text("等待下一次 Top Process 采样…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                health.topProcesses.take(6).forEach { process -> ProcessRow(process) }
            }
        }
    }
}

@Composable
private fun ProcessRow(process: ProcessHealth) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(process.processName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text("PID ${process.pid} · ${process.user} · RSS ${process.rss}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("%.1f%%".format(process.cpuPercent), fontWeight = FontWeight.Bold, color = if (process.isRootShell && process.cpuPercent >= 50f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PerformanceScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onModeSelected: (PerformanceMode) -> Unit,
    onReleaseCaps: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { DetailHeader("性能控制", "温度驱动 · 不关闭 Samsung Thermal", onBack, state.loading, onRefresh) }
            item { HeroCard(state.snapshot, state.mode) }
            item {
                PerformanceModeCard(
                    mode = state.mode,
                    stage = state.snapshot.thermalStage(),
                    busy = state.actionInProgress,
                    onModeSelected = onModeSelected,
                )
            }
            item { SectionLabel("CPU 实时状态", "WALT 继续负责调度，Root Tools 只做安全限峰") }
            itemsIndexed(state.snapshot.cpuClusters) { index, cluster ->
                val capState = state.cpuCapStates.firstOrNull { it.policyId == cluster.policyId }
                CpuClusterCard(
                    cluster,
                    when {
                        index == 0 -> "能效核"
                        index == state.snapshot.cpuClusters.lastIndex && index >= 2 -> "超大核"
                        else -> "性能核"
                    },
                    capState,
                )
            }
            item {
                OutlinedButton(
                    onClick = onReleaseCaps,
                    enabled = !state.actionInProgress && state.cpuCapStates.any { it.source == com.arthur.roottools.model.CpuCapSource.ROOT_TOOLS },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("释放 Root Tools 自己拥有的 CPU cap")
                }
            }
            item { SectionLabel("策略历史", "只记录模式变化与真实 cap 写入，不记录轮询") }
            item { CpuPolicyHistoryCard(state) }
            state.actionMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            state.error?.let { item { ErrorCard(it) } }
            item { SafetyCard() }
        }
    }
}

@Composable
private fun CpuPolicyHistoryCard(state: DashboardUiState) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.cpuPolicyEvents.isEmpty()) {
                Text("暂无策略变化记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.cpuPolicyEvents.take(12).forEachIndexed { index, event ->
                    if (index > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(event.type.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(event.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatRelativeTime(event.timestampMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdbScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAdbToggle: () -> Unit,
) {
    val context = LocalContext.current
    var confirmDisable by remember { mutableStateOf(false) }
    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("关闭 Root ADB？") },
            text = {
                Text("当前管理链路可能正通过 Tailscale + ADB 5555 连接。关闭后这次远程会话可能立即失联，且重启后 5555 尚未保证自动恢复。")
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
            item { DetailHeader("Root ADB", "移动网络也可配合 Tailscale 使用", onBack, state.loading, onRefresh) }
            item {
                AdbCard(
                    snapshot = state.snapshot,
                    busy = state.actionInProgress,
                    onToggle = {
                        if (state.snapshot.adbEnabled) confirmDisable = true else onAdbToggle()
                    },
                    onCopy = { copyToClipboard(context, it) },
                    onDeveloperSettings = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                    },
                )
            }
            item { QuickTileCard() }
        }
    }
}

@Composable
private fun StartupScreen(
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
    val frameworkCatalog = state.startup.dataSource == StartupDataSource.FRAMEWORK_CATALOG
    val candidates = if (frameworkCatalog) {
        state.startup.apps.sortedBy { it.label.lowercase() }.take(80)
    } else {
        state.startup.apps
            .filter { it.category != AppPolicyCategory.NORMAL || it.disabled || it.running || it.bootReceiverCount > 0 }
            .sortedWith(compareBy<StartupAppRecord> { categoryOrder(it.category) }.thenByDescending { it.startupRiskScore })
            .take(40)
    }

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
                    subtitle = "Freeze · Standby · AppOps · Test Mode",
                    onBack = onBack,
                    loading = state.startupLoading || state.actionInProgress,
                    onRefresh = onRefresh,
                )
            }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Appium 测试模式", fontWeight = FontWeight.Bold)
                            Text(
                                if (frameworkCatalog) "Framework catalog 模式不推断当前 Appium 状态" else "Notification Listener + Doze whitelist 按需启用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.startup.appiumTestMode, onCheckedChange = onAppiumMode, enabled = !state.actionInProgress && !frameworkCatalog)
                    }
                }
            }
            if (frameworkCatalog) {
                item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f), shape = RoundedCornerShape(16.dp)) {
                        Text(
                            "当前没有 Root boot trace，因此使用本地 PackageManager + Shizuku/Sui 的降级模式。Freeze / Enable / Force stop / Standby / AppOps 仍通过 typed privilege backend 执行；运行中状态不做猜测。",
                            Modifier.padding(13.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
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
                        busy = state.actionInProgress,
                        onFreeze = { pendingFreeze = app },
                        onEnable = { onEnable(app.packageName) },
                        onForceStop = { onForceStop(app.packageName) },
                        onRare = { onBucket(app.packageName, 40) },
                        onRestricted = { onBucket(app.packageName, 45) },
                        onAllowBackground = { onBackground(app.packageName, true) },
                        onIgnoreBackground = { onBackground(app.packageName, false) },
                        runtimeKnown = !frameworkCatalog,
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
    runtimeKnown: Boolean,
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
                "${if (runtimeKnown) if (app.running) "Running" else "Stopped" else "Runtime —"} · ${if (app.disabled) "Frozen" else "Enabled"} · Bucket ${app.standbyBucket ?: "—"} · Boot ${app.bootReceiverCount}",
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
private fun DiagnosticsScreen(
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
private fun ModuleCenterScreen(
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
private fun CommonActionsScreen(
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
                        Text("允许：SET_MODE / SET_ADB / RUN_DIAGNOSTIC / FREEZE / UNFREEZE", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun NetworkDiagnosticsScreen(
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
private fun StorageDiagnosticsScreen(
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
private fun BatteryThermalScreen(
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
                            TemperatureSparkline(history)
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
private fun PermissionScreen(
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
                            Text(definition.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (definition.requiredCapabilities.isEmpty()) {
                                    "无需额外能力"
                                } else {
                                    definition.requiredCapabilities.joinToString(" · ") { capabilityLabel(it) }
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
                                if (missing.isEmpty()) "READY" else "缺 ${missing.joinToString { capabilityLabel(it) }}",
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

private fun capabilityAvailable(capability: ToolCapability, state: DashboardUiState): Boolean = when (capability) {
    ToolCapability.ROOT -> state.snapshot.rootAvailable
    ToolCapability.NOTIFICATION -> state.notificationsGranted
    ToolCapability.MAGISK -> state.modules.magiskModules.isNotEmpty()
    ToolCapability.VECTOR -> state.modules.vectorActive
    ToolCapability.NETWORK -> true // Network diagnostics can open offline and explain missing links.
    ToolCapability.SHIZUKU -> state.shizuku.ready
    ToolCapability.FRAMEWORK_PRIVILEGE -> state.shizuku.ready || state.snapshot.rootAvailable
}

private fun capabilityLabel(capability: ToolCapability): String = when (capability) {
    ToolCapability.ROOT -> "Root"
    ToolCapability.NOTIFICATION -> "通知"
    ToolCapability.MAGISK -> "Magisk"
    ToolCapability.VECTOR -> "Vector"
    ToolCapability.NETWORK -> "网络"
    ToolCapability.SHIZUKU -> "Shizuku / Sui"
    ToolCapability.FRAMEWORK_PRIVILEGE -> "Root / Shizuku"
}

@Composable
private fun ComponentManagerScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onSelectPackage: (String) -> Unit,
    onClosePackage: () -> Unit,
    onSetEnabled: (AppComponentRecord, Boolean) -> Unit,
) {
    val snapshot = state.componentSnapshot
    if (snapshot != null) {
        ComponentPackageScreen(
            state = state,
            onBack = onClosePackage,
            onRefresh = { onSelectPackage(snapshot.packageName) },
            onSetEnabled = onSetEnabled,
        )
        return
    }

    var query by rememberSaveable { mutableStateOf("") }
    val apps = remember(state.componentCatalog, query) {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) state.componentCatalog else state.componentCatalog.filter {
            it.label.lowercase().contains(needle) || it.packageName.lowercase().contains(needle)
        }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DetailHeader(
                    "组件管理",
                    "Activity · Service · Receiver · Provider",
                    onBack,
                    state.componentCatalogLoading,
                    onRefreshCatalog,
                )
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        "读取使用本地 PackageManager；修改统一经过 PrivilegeRouter。Shizuku / Sui Ready 时优先 Binder，RootShell 仅作为安全 fallback。系统 App 第一版保持只读。",
                        Modifier.padding(15.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("搜索应用 / package") },
                )
            }
            item { SectionLabel("用户应用", "${apps.size} / ${state.componentCatalog.size} · 按需加载组件，不后台轮询") }
            if (state.componentCatalogLoading && state.componentCatalog.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else {
                itemsIndexed(apps, key = { _, app -> app.packageName }) { _, app ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectPackage(app.packageName) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(if (app.enabled) "ENABLED" else "DISABLED", style = MaterialTheme.typography.labelSmall, color = if (app.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
            state.error?.let { item { ErrorCard(it) } }
        }
    }
}

@Composable
private fun ComponentPackageScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetEnabled: (AppComponentRecord, Boolean) -> Unit,
) {
    val snapshot = state.componentSnapshot ?: return
    var selectedKind by rememberSaveable(snapshot.packageName) { mutableStateOf<ComponentKind?>(null) }
    var filter by rememberSaveable(snapshot.packageName) { mutableStateOf(ComponentViewFilter.ALL) }
    var pending by remember { mutableStateOf<Pair<AppComponentRecord, Boolean>?>(null) }

    pending?.let { (component, enabled) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("${if (enabled) "启用" else "停用"} ${component.kind.displayName}？") },
            text = {
                Text(
                    if (enabled) {
                        "将恢复 ${component.className}。操作会记录 before / after 与实际 Backend。"
                    } else {
                        "停用组件可能改变应用启动、推送或后台行为。关键 Launcher、受保护应用和系统 App 已由安全策略禁止修改。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { pending = null; onSetEnabled(component, enabled) }) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } },
        )
    }

    val components = remember(snapshot, selectedKind, filter) {
        snapshot.components.filter { component ->
            (selectedKind == null || component.kind == selectedKind) && when (filter) {
                ComponentViewFilter.ALL -> true
                ComponentViewFilter.BOOT -> component.bootReceiver
                ComponentViewFilter.EXPORTED -> component.exported
                ComponentViewFilter.FGS -> component.foregroundService
                ComponentViewFilter.DISABLED -> !component.enabled
            }
        }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { DetailHeader(snapshot.label, snapshot.packageName, onBack, state.componentLoading, onRefresh) }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HealthMetric("Components", snapshot.components.size.toString(), "total", Modifier.weight(1f))
                            HealthMetric("BOOT", snapshot.bootReceiverCount.toString(), "receivers", Modifier.weight(1f))
                            HealthMetric("Disabled", snapshot.disabledCount.toString(), "overrides", Modifier.weight(1f))
                        }
                        SummaryRow("Exported", snapshot.exportedCount.toString())
                        SummaryRow("Foreground service", snapshot.foregroundServiceCount.toString())
                        SummaryRow("Write backend", if (state.shizuku.ready) state.shizuku.backend.displayName else if (state.snapshot.rootAvailable) "RootShell fallback" else "Unavailable")
                        if (snapshot.systemApp) {
                            Text("系统 App 第一版只读。", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    item { FilterChip(selected = selectedKind == null, onClick = { selectedKind = null }, label = { Text("ALL") }) }
                    items(ComponentKind.entries.size) { index ->
                        val kind = ComponentKind.entries[index]
                        FilterChip(selected = selectedKind == kind, onClick = { selectedKind = kind }, label = { Text(kind.displayName) })
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(ComponentViewFilter.entries.size) { index ->
                        val option = ComponentViewFilter.entries[index]
                        FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option.displayName) })
                    }
                }
            }
            item { SectionLabel("组件", "${components.size} / ${snapshot.components.size} · 仅显式操作才写系统") }
            if (components.isEmpty()) {
                item { Text("当前筛选没有组件", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                itemsIndexed(components, key = { _, component -> component.componentName }) { _, component ->
                    val safety = ComponentSafetyPolicy.evaluate(snapshot, component)
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(component.className.substringAfterLast('.'), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(component.kind.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    buildList {
                                        if (component.bootReceiver) add("BOOT")
                                        if (component.exported) add("EXPORTED")
                                        if (component.foregroundService) add("FGS")
                                        if (component.directBootAware) add("DIRECT_BOOT")
                                        component.protectedReason?.let(::add)
                                    }.joinToString(" · ").ifBlank { component.permission ?: "manifest component" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = component.enabled,
                                onCheckedChange = { enabled -> pending = component to enabled },
                                enabled = safety.allowed && !state.actionInProgress,
                            )
                        }
                    }
                }
            }
            state.actionMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
            state.error?.let { item { ErrorCard(it) } }
        }
    }
}

@Composable
private fun ShizukuSuiScreen(
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
            item { SectionLabel("Capability self-test", "只读验证 UserService / Package / Activity / AppOps，不修改系统状态") }
            item {
                OutlinedButton(
                    onClick = onSelfTest,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = bridge.ready && !state.shizukuSelfTestRunning,
                ) {
                    if (state.shizukuSelfTestRunning) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.shizukuProbes.isEmpty()) "运行只读 Self-test" else "重新运行 Self-test")
                }
            }
            if (state.shizukuProbes.isNotEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.shizukuProbes.forEach { probe ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(probe.capability.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${probe.backend.displayName} · ${probe.detail} · ${probe.latencyMs?.let { "%.1f ms".format(it) } ?: "—"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        if (probe.available) "PASS" else "FAIL",
                                        color = if (probe.available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
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
private fun DetailHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRefresh, enabled = !loading) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
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
                Text("编辑快捷面板，添加“CPU 档位”和“Root ADB”。CPU 磁贴单击循环 Auto / Cool / Performance。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun SectionLabel(title: String, subtitle: String) {
    Column(Modifier.padding(top = 4.dp, bottom = 1.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.11f), shape = RoundedCornerShape(18.dp)) {
        Text(message, Modifier.padding(15.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

private fun formatGHz(khz: Long): String = if (khz <= 0L) "—" else "%.2f GHz".format(khz / 1_000_000.0)

private fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "—"
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

private fun formatRelativeTime(timestampMs: Long): String {
    val delta = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    return when {
        delta < 60_000L -> "${delta / 1_000L}s ago"
        delta < 60 * 60_000L -> "${delta / 60_000L}m ago"
        delta < 24 * 60 * 60_000L -> "${delta / (60 * 60_000L)}h ago"
        else -> "${delta / (24 * 60 * 60_000L)}d ago"
    }
}

private fun formatStartupSeconds(seconds: Long?): String = when {
    seconds == null -> "—"
    seconds < 60 -> "${seconds}s"
    seconds < 3_600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3_600}h ${(seconds % 3_600) / 60}m"
}

private fun categoryOrder(category: AppPolicyCategory): Int = when (category) {
    AppPolicyCategory.FREEZE -> 0
    AppPolicyCategory.ON_DEMAND -> 1
    AppPolicyCategory.RARE -> 2
    AppPolicyCategory.PROTECTED -> 3
    AppPolicyCategory.NORMAL -> 4
}

private fun DeviceHealthSnapshot.thermalStageLabel(): String = when {
    thermal.status >= 3 -> "Severe"
    thermal.status >= 2 -> "Moderate"
    thermal.status >= 1 -> "Warm"
    (thermal.skinC ?: 0f) >= 39f -> "Moderate"
    (thermal.skinC ?: 0f) >= 36.5f -> "Warm"
    else -> "Normal"
}

private fun rangeText(values: List<Float>): String = if (values.isEmpty()) {
    "—"
} else {
    "%.1f°C / %.1f°C".format(values.minOrNull() ?: 0f, values.maxOrNull() ?: 0f)
}

private data class FrequencyBins(
    val low: Int = 0,
    val mid: Int = 0,
    val high: Int = 0,
    val peak: Int = 0,
    val total: Int = 0,
) {
    fun asLabel(): String {
        if (total <= 0) return "—"
        fun pct(value: Int) = (value * 100f / total).roundToInt()
        return "${pct(low)}% · ${pct(mid)}% · ${pct(high)}% · ${pct(peak)}%"
    }
}

private fun frequencyBins(
    history: List<HealthHistoryPoint>,
    policyId: Int,
    hardwareMaxKHz: Long,
    windowMs: Long,
): FrequencyBins {
    if (hardwareMaxKHz <= 0) return FrequencyBins()
    val cutoff = System.currentTimeMillis() - windowMs
    var low = 0
    var mid = 0
    var high = 0
    var peak = 0
    var total = 0
    history.asSequence().filter { it.timestampMs >= cutoff }.forEach { point ->
        val current = point.clusterCurrentKHz[policyId] ?: return@forEach
        val ratio = current.toDouble() / hardwareMaxKHz
        when {
            ratio < 0.35 -> low++
            ratio < 0.60 -> mid++
            ratio < 0.80 -> high++
            else -> peak++
        }
        total++
    }
    return FrequencyBins(low, mid, high, peak, total)
}

private fun formatMemoryKb(kb: Long): String = when {
    kb >= 1024 * 1024 -> "%.2f GB".format(kb / 1_048_576.0)
    kb >= 1024 -> "%.0f MB".format(kb / 1024.0)
    else -> "$kb KB"
}

private fun formatClockTime(timestampMs: Long): String =
    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestampMs))

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Root Tools", text))
}

private fun openPackage(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    runCatching { context.startActivity(intent) }
}

private fun shareDiagnosticFile(context: Context, path: String) {
    val file = File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "分享诊断报告")) }
}
