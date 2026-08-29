package com.arthur.roottools.app.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arthur.roottools.R
import com.arthur.roottools.app.navigation.RootToolsDestination
import com.arthur.roottools.app.navigation.RootToolsNavigationPolicy
import com.arthur.roottools.app.navigation.RootToolsTab
import com.arthur.roottools.app.navigation.ToolDefinition
import com.arthur.roottools.app.navigation.ToolId
import com.arthur.roottools.app.navigation.ToolRegistry
import com.arthur.roottools.core.presentation.labelRes
import com.arthur.roottools.core.ui.component.RootToolsSectionHeader
import com.arthur.roottools.core.ui.component.RootToolsStatusChip
import com.arthur.roottools.core.ui.token.RootToolsRadius
import com.arthur.roottools.core.ui.token.RootToolsSpacing
import com.arthur.roottools.core.ui.token.RootToolsStatusTone
import com.arthur.roottools.feature.home.presentation.HomeHealthInput
import com.arthur.roottools.feature.home.presentation.HomeHealthPolicy
import com.arthur.roottools.root.RootAuthorizationStatus
import com.arthur.roottools.ui.DashboardUiState
import com.arthur.roottools.ui.capabilityAvailable

@Composable
internal fun DomainLandingScreen(
    tab: RootToolsTab,
    state: DashboardUiState,
    onNavigate: (RootToolsDestination) -> Unit,
) {
    require(tab != RootToolsTab.HOME)
    val tools = ToolRegistry.tools.filter {
        RootToolsNavigationPolicy.destinationFor(it.id).tab == tab
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = RootToolsSpacing.md,
                end = RootToolsSpacing.md,
                top = RootToolsSpacing.md,
                bottom = RootToolsSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.md),
        ) {
            item { DomainHero(tab, state) }
            item {
                RootToolsSectionHeader(
                    title = stringResource(R.string.hub_tools_title),
                    subtitle = stringResource(R.string.hub_tools_subtitle),
                )
            }
            items(tools, key = { it.id.name }) { definition ->
                DomainToolRow(
                    definition = definition,
                    state = state,
                    onClick = {
                        onNavigate(RootToolsNavigationPolicy.destinationFor(definition.id))
                    },
                )
            }
        }
    }
}
@Composable
private fun DomainHero(tab: RootToolsTab, state: DashboardUiState) {
    val titleRes: Int
    val subtitleRes: Int
    val icon: ImageVector
    val status: String
    when (tab) {
        RootToolsTab.APPS -> {
            titleRes = R.string.hub_apps_title
            subtitleRes = R.string.hub_apps_subtitle
            icon = Icons.Rounded.Apps
            status = if (state.appInventory.apps.isNotEmpty()) {
                stringResource(
                    R.string.hub_apps_status_loaded,
                    state.appInventory.apps.size,
                    state.appInventory.runningApps,
                    state.appInventory.frozenApps,
                )
            } else {
                stringResource(R.string.hub_apps_status_idle)
            }
        }
        RootToolsTab.DEVICE -> {
            titleRes = R.string.hub_device_title
            subtitleRes = R.string.hub_device_subtitle
            icon = Icons.Rounded.Devices
            status = stringResource(
                R.string.hub_device_status,
                stringResource(state.mode.labelRes()),
                temperatureText(state.health.thermal.skinC ?: state.snapshot.skinTempC),
                stringResource(if (state.adb.rootTcpEnabled || state.adb.nativeWirelessEnabled) R.string.hub_status_on else R.string.hub_status_off),
            )
        }
        RootToolsTab.DIAGNOSTICS -> {
            titleRes = R.string.hub_diagnostics_title
            subtitleRes = R.string.hub_diagnostics_subtitle
            icon = Icons.Rounded.Terminal
            val abnormal = maxOf(
                state.diagnostics.abnormalRootShells,
                if (state.health.abnormalRootShell != null) 1 else 0,
            )
            val attention = HomeHealthPolicy.decide(
                HomeHealthInput(
                    rootAvailable = state.snapshot.rootAvailable,
                    metricsAvailable = state.health.rootAvailable,
                    cpuUsagePercent = state.health.cpuUsagePercent,
                    thermalStatus = state.health.thermal.status,
                    skinC = state.health.thermal.skinC ?: state.snapshot.skinTempC,
                    abnormalRootShells = abnormal,
                    memoryStatus = state.health.memory.status,
                ),
            ).attention.size
            status = stringResource(
                R.string.hub_diagnostics_status,
                state.health.cpuUsagePercent,
                temperatureText(state.health.thermal.skinC ?: state.snapshot.skinTempC),
                attention,
            )
        }
        RootToolsTab.SYSTEM -> {
            titleRes = R.string.hub_system_title
            subtitleRes = R.string.hub_system_subtitle
            icon = Icons.Rounded.Settings
            val rootStatus = when {
                state.snapshot.rootAvailable -> stringResource(R.string.hub_status_ready)
                state.rootAuthorization.status == RootAuthorizationStatus.REQUESTING -> stringResource(R.string.hub_status_requesting)
                state.rootAuthorization.status == RootAuthorizationStatus.DENIED_OR_TIMEOUT -> stringResource(R.string.hub_status_not_authorized)
                else -> stringResource(R.string.hub_status_unavailable)
            }
            status = stringResource(
                R.string.hub_system_status,
                rootStatus,
                stringResource(if (state.shizuku.ready) R.string.hub_status_ready else R.string.hub_status_off),
            )
        }
        RootToolsTab.HOME -> error("HOME uses ProductHomeScreen")
    }

    Card(
        shape = RoundedCornerShape(RootToolsRadius.dialog),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(RootToolsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(RootToolsRadius.card),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Column(Modifier.weight(1f).padding(start = RootToolsSpacing.sm)) {
                    Text(stringResource(titleRes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(subtitleRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
@Composable
private fun DomainToolRow(
    definition: ToolDefinition,
    state: DashboardUiState,
    onClick: () -> Unit,
) {
    val missing = definition.requiredCapabilities.filterNot { capabilityAvailable(it, state) }
    val title = stringResource(definition.titleRes)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(RootToolsRadius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(RootToolsSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RootToolsSpacing.sm),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(RootToolsRadius.chip),
                color = definition.accent.copy(alpha = 0.14f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(definition.icon, contentDescription = null, tint = definition.accent)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (missing.isNotEmpty()) {
                        RootToolsStatusChip(
                            label = stringResource(R.string.hub_capability_missing),
                            tone = RootToolsStatusTone.Warning,
                        )
                    }
                }
                Text(
                    text = toolStatus(definition.id, state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.hub_open_tool, title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun toolStatus(toolId: ToolId, state: DashboardUiState): String = when (toolId) {
    ToolId.DASHBOARD -> stringResource(R.string.hub_tool_health_desc)
    ToolId.PERFORMANCE -> stringResource(R.string.hub_tool_performance_desc, stringResource(state.mode.labelRes()))
    ToolId.SHADOW_DISPLAY -> stringResource(R.string.shadow_display_home_subtitle)
    ToolId.AGENT_SESSION -> stringResource(R.string.agent_session_home_subtitle)
    ToolId.ROOT_ADB -> if (state.adb.rootTcpEnabled) {
        val host = state.adb.tailscaleIpv4 ?: state.adb.localIpv4
        if (host != null) {
            stringResource(R.string.common_endpoint, host, state.adb.rootTcpPort ?: 5555)
        } else {
            stringResource(R.string.hub_tool_adb_desc)
        }
    } else {
        stringResource(R.string.hub_tool_adb_desc)
    }
    ToolId.ROOT_TAILSCALE -> stringResource(R.string.root_tailscale_home_desc)
    ToolId.PERMISSIONS -> stringResource(R.string.hub_tool_permissions_desc)
    ToolId.STARTUP -> stringResource(R.string.hub_tool_startup_desc)
    ToolId.APPS -> stringResource(R.string.hub_tool_apps_desc)
    ToolId.AD_GOVERNANCE -> stringResource(R.string.ad_governance_home_subtitle)
    ToolId.COMPONENTS -> stringResource(R.string.app_control_components_subtitle)
    ToolId.PERMISSION_OPS -> stringResource(R.string.hub_tool_appops_desc)
    ToolId.DIAGNOSTICS -> if (state.diagnostics.abnormalRootShells > 0) {
        stringResource(R.string.hub_tool_diagnostics_abnormal, state.diagnostics.abnormalRootShells)
    } else {
        stringResource(R.string.hub_tool_diagnostics_desc)
    }
    ToolId.INTEGRITY -> stringResource(R.string.hub_tool_integrity_desc)
    ToolId.MODULES -> if (state.modules.magiskModules.isNotEmpty() || state.modules.vectorModules.isNotEmpty()) {
        stringResource(R.string.hub_tool_modules_loaded, state.modules.enabledMagiskCount, state.modules.enabledVectorCount)
    } else {
        stringResource(R.string.hub_tool_modules_desc)
    }
    ToolId.ACTIONS -> if (state.favoriteActions.isNotEmpty()) {
        stringResource(R.string.hub_tool_actions_loaded, state.favoriteActions.size)
    } else {
        stringResource(R.string.hub_tool_actions_desc)
    }
    ToolId.NETWORK -> stringResource(R.string.hub_tool_network_desc)
    ToolId.STORAGE -> stringResource(R.string.hub_tool_storage_desc)
    ToolId.BATTERY -> stringResource(
        R.string.hub_tool_battery_live,
        state.health.battery.level?.let { stringResource(R.string.common_percent_int, it) }
            ?: stringResource(R.string.common_dash),
        temperatureText(state.health.thermal.skinC ?: state.snapshot.skinTempC),
    )
    ToolId.SHIZUKU -> stringResource(R.string.hub_tool_shizuku_desc)
    ToolId.DEVELOPER_RUNTIME -> stringResource(R.string.hub_tool_developer_desc)
    ToolId.COMPANION_SUITE -> stringResource(R.string.companion_suite_home_desc)
    ToolId.ASSISTANT -> stringResource(R.string.hub_tool_assistant_desc)
}
