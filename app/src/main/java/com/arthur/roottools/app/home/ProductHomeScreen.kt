package com.arthur.roottools.app.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arthur.roottools.R
import com.arthur.roottools.app.navigation.RootToolsDestination
import com.arthur.roottools.core.agent.AgentSessionState
import com.arthur.roottools.core.agent.AgentSessionStatus
import com.arthur.roottools.core.presentation.labelRes
import com.arthur.roottools.core.ui.component.RootToolsSectionHeader
import com.arthur.roottools.core.ui.component.RootToolsStatusChip
import com.arthur.roottools.core.ui.token.RootToolsRadius
import com.arthur.roottools.core.ui.token.RootToolsSpacing
import com.arthur.roottools.core.ui.token.RootToolsStatusTone
import com.arthur.roottools.feature.home.presentation.HomeAttentionType
import com.arthur.roottools.feature.home.presentation.HomeHealthDecision
import com.arthur.roottools.feature.home.presentation.HomeHealthInput
import com.arthur.roottools.feature.home.presentation.HomeHealthPolicy
import com.arthur.roottools.feature.home.presentation.HomeHealthVerdict
import com.arthur.roottools.feature.home.presentation.HomeTimelineEntry
import com.arthur.roottools.feature.home.presentation.HomeTimelineKind
import com.arthur.roottools.ui.DashboardUiState
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ProductHomeScreen(
    state: DashboardUiState,
    agentSession: AgentSessionState,
    onRefresh: () -> Unit,
    onNavigate: (RootToolsDestination) -> Unit,
) {
    val abnormalRootShells = maxOf(
        state.diagnostics.abnormalRootShells,
        if (state.health.abnormalRootShell != null) 1 else 0,
    )
    val decision = HomeHealthPolicy.decide(
        HomeHealthInput(
            rootAvailable = state.snapshot.rootAvailable,
            metricsAvailable = state.health.rootAvailable,
            cpuUsagePercent = state.health.cpuUsagePercent,
            thermalStatus = state.health.thermal.status,
            skinC = state.health.thermal.skinC ?: state.snapshot.skinTempC,
            abnormalRootShells = abnormalRootShells,
            memoryStatus = state.health.memory.status,
        ),
    )
    val timeline = HomeHealthPolicy.mergeTimeline(state.cpuPolicyEvents, state.auditRecords)

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
            item {
                HomeHeader(
                    model = state.snapshot.model,
                    modeLabel = stringResource(state.mode.labelRes()),
                    rootAvailable = state.snapshot.rootAvailable,
                    loading = state.loading,
                    onRefresh = onRefresh,
                )
            }
            item { HealthVerdictCard(state, decision) }
            if (agentSession.active) {
                item { ActiveAgentCard(agentSession, onNavigate) }
            }
            item {
                RootToolsSectionHeader(
                    title = stringResource(R.string.home_quick_title),
                    subtitle = stringResource(R.string.home_quick_subtitle),
                )
            }
            item { QuickActions(onNavigate) }
            item {
                RootToolsSectionHeader(
                    title = stringResource(R.string.home_attention_title),
                    subtitle = stringResource(R.string.home_attention_subtitle),
                )
            }
            item { AttentionCard(state, decision, abnormalRootShells, onNavigate) }
            item {
                RootToolsSectionHeader(
                    title = stringResource(R.string.home_recent_title),
                    subtitle = stringResource(R.string.home_recent_subtitle),
                )
            }
            item { RecentActivityCard(timeline) }
        }
    }
}

@Composable
private fun ActiveAgentCard(
    session: AgentSessionState,
    onNavigate: (RootToolsDestination) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onNavigate(RootToolsDestination.AGENT_SESSION) },
        shape = RoundedCornerShape(RootToolsRadius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(RootToolsSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RootToolsSpacing.sm),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(RootToolsRadius.chip),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ChatBubble, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session.title.ifBlank { stringResource(R.string.agent_session_title) },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    RootToolsStatusChip(
                        label = stringResource(
                            when (session.status) {
                                AgentSessionStatus.RUNNING -> R.string.agent_status_running
                                AgentSessionStatus.PAUSED -> R.string.agent_status_paused
                                AgentSessionStatus.WAITING_USER -> R.string.agent_status_waiting
                                else -> R.string.agent_status_running
                            }
                        ),
                        tone = when (session.status) {
                            AgentSessionStatus.RUNNING -> RootToolsStatusTone.Success
                            AgentSessionStatus.WAITING_USER -> RootToolsStatusTone.Warning
                            else -> RootToolsStatusTone.Neutral
                        },
                    )
                }
                Text(
                    text = session.currentStep.ifBlank { stringResource(R.string.agent_session_idle_step) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun HomeHeader(
    model: String,
    modeLabel: String,
    rootAvailable: Boolean,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RootToolsSpacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_product_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.home_product_subtitle, model, modeLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RootToolsStatusChip(
            label = stringResource(if (rootAvailable) R.string.common_root else R.string.common_no_root),
            tone = if (rootAvailable) RootToolsStatusTone.Privileged else RootToolsStatusTone.Warning,
        )
        IconButton(onClick = onRefresh, enabled = !loading) {
            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.common_refresh))
        }
    }
}

@Composable
private fun HealthVerdictCard(
    state: DashboardUiState,
    decision: HomeHealthDecision,
) {
    val title = when (decision.verdict) {
        HomeHealthVerdict.LOADING -> R.string.home_verdict_loading_title
        HomeHealthVerdict.GOOD -> R.string.home_verdict_good_title
        HomeHealthVerdict.SETUP -> R.string.home_verdict_setup_title
        HomeHealthVerdict.BUSY -> R.string.home_verdict_busy_title
        HomeHealthVerdict.WARM -> R.string.home_verdict_warm_title
        HomeHealthVerdict.CRITICAL -> R.string.home_verdict_critical_title
    }
    val detail = when (decision.verdict) {
        HomeHealthVerdict.LOADING -> R.string.home_verdict_loading_detail
        HomeHealthVerdict.GOOD -> R.string.home_verdict_good_detail
        HomeHealthVerdict.SETUP -> R.string.home_verdict_setup_detail
        HomeHealthVerdict.BUSY -> R.string.home_verdict_busy_detail
        HomeHealthVerdict.WARM -> R.string.home_verdict_warm_detail
        HomeHealthVerdict.CRITICAL -> R.string.home_verdict_critical_detail
    }
    val tone = when (decision.verdict) {
        HomeHealthVerdict.LOADING -> RootToolsStatusTone.Info
        HomeHealthVerdict.GOOD -> RootToolsStatusTone.Success
        HomeHealthVerdict.SETUP -> RootToolsStatusTone.Info
        HomeHealthVerdict.BUSY -> RootToolsStatusTone.Warning
        HomeHealthVerdict.WARM -> RootToolsStatusTone.Warning
        HomeHealthVerdict.CRITICAL -> RootToolsStatusTone.Danger
    }
    val skin = temperatureText(state.health.thermal.skinC ?: state.snapshot.skinTempC)
    val battery = state.health.battery.level ?: state.snapshot.batteryLevel

    Card(
        shape = RoundedCornerShape(RootToolsRadius.dialog),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ),
                )
                .padding(RootToolsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RootToolsStatusChip(
                    label = stringResource(state.mode.labelRes()),
                    tone = tone,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(RootToolsSpacing.xs)) {
                HomeMetricChip(
                    text = stringResource(R.string.home_metric_skin, skin),
                    modifier = Modifier.weight(1f),
                )
                HomeMetricChip(
                    text = stringResource(
                        R.string.home_metric_cpu,
                        if (state.health.rootAvailable) {
                            stringResource(R.string.common_percent_float, state.health.cpuUsagePercent)
                        } else {
                            stringResource(R.string.common_dash)
                        },
                    ),
                    modifier = Modifier.weight(1f),
                )
                HomeMetricChip(
                    text = stringResource(
                        R.string.home_metric_battery,
                        battery?.let { stringResource(R.string.common_percent_int, it) }
                            ?: stringResource(R.string.common_dash),
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HomeMetricChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(RootToolsRadius.card),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = RootToolsSpacing.sm, vertical = RootToolsSpacing.xs),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QuickActions(onNavigate: (RootToolsDestination) -> Unit) {
    val items = listOf(
        QuickAction(R.string.tool_performance_title, Icons.Rounded.Speed, RootToolsDestination.PERFORMANCE),
        QuickAction(R.string.tool_adb_title, Icons.Rounded.WifiTethering, RootToolsDestination.ADB),
        QuickAction(R.string.tool_apps_title, Icons.Rounded.Apps, RootToolsDestination.APP_CONTROL),
        QuickAction(R.string.nav_diagnostics, Icons.Rounded.Terminal, RootToolsDestination.PROCESS_DIAGNOSTICS),
    )
    Column(verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.xs)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RootToolsSpacing.xs),
            ) {
                rowItems.forEach { item ->
                    QuickActionCard(
                        item = item,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(item.destination) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    item: QuickAction,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.height(92.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(RootToolsRadius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(RootToolsSpacing.md),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(item.labelRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AttentionCard(
    state: DashboardUiState,
    decision: HomeHealthDecision,
    abnormalRootShells: Int,
    onNavigate: (RootToolsDestination) -> Unit,
) {
    if (decision.attention.isEmpty()) {
        AttentionRow(
            icon = Icons.Rounded.VerifiedUser,
            title = stringResource(R.string.home_attention_none_title),
            detail = stringResource(R.string.home_attention_none_detail),
            tone = RootToolsStatusTone.Success,
            onClick = null,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.xs)) {
        decision.attention.forEach { type ->
            when (type) {
                HomeAttentionType.ROOT_SHELL -> AttentionRow(
                    icon = Icons.Rounded.Warning,
                    title = stringResource(R.string.home_attention_root_shell_title),
                    detail = stringResource(R.string.home_attention_root_shell_detail, abnormalRootShells),
                    tone = RootToolsStatusTone.Danger,
                    onClick = { onNavigate(RootToolsDestination.PROCESS_DIAGNOSTICS) },
                )
                HomeAttentionType.THERMAL -> AttentionRow(
                    icon = Icons.Rounded.Thermostat,
                    title = stringResource(R.string.home_attention_thermal_title),
                    detail = stringResource(
                        R.string.home_attention_thermal_detail,
                        temperatureText(state.health.thermal.skinC ?: state.snapshot.skinTempC),
                        state.health.thermal.status,
                    ),
                    tone = RootToolsStatusTone.Warning,
                    onClick = { onNavigate(RootToolsDestination.PERFORMANCE) },
                )
                HomeAttentionType.CPU -> AttentionRow(
                    icon = Icons.Rounded.Speed,
                    title = stringResource(R.string.home_attention_cpu_title),
                    detail = stringResource(R.string.home_attention_cpu_detail, state.health.cpuUsagePercent),
                    tone = RootToolsStatusTone.Warning,
                    onClick = { onNavigate(RootToolsDestination.HEALTH_DASHBOARD) },
                )
                HomeAttentionType.MEMORY -> AttentionRow(
                    icon = Icons.Rounded.Memory,
                    title = stringResource(R.string.home_attention_memory_title),
                    detail = stringResource(R.string.home_attention_memory_detail),
                    tone = RootToolsStatusTone.Warning,
                    onClick = { onNavigate(RootToolsDestination.HEALTH_DASHBOARD) },
                )
                HomeAttentionType.ROOT -> AttentionRow(
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.home_attention_root_title),
                    detail = stringResource(R.string.home_attention_root_detail),
                    tone = RootToolsStatusTone.Info,
                    onClick = { onNavigate(RootToolsDestination.PERMISSIONS) },
                )
            }
        }
    }
}

@Composable
private fun AttentionRow(
    icon: ImageVector,
    title: String,
    detail: String,
    tone: RootToolsStatusTone,
    onClick: (() -> Unit)?,
) {
    val modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RootToolsRadius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(RootToolsSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RootToolsSpacing.sm),
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(RootToolsRadius.chip),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    RootToolsStatusChip(label = stringResource(R.string.home_attention_title), tone = tone)
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) Icon(Icons.Rounded.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun RecentActivityCard(entries: List<HomeTimelineEntry>) {
    Card(
        shape = RoundedCornerShape(RootToolsRadius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.home_recent_empty),
                modifier = Modifier.padding(RootToolsSpacing.md),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(Modifier.padding(RootToolsSpacing.md)) {
                entries.forEachIndexed { index, entry ->
                    TimelineRow(entry)
                    if (index != entries.lastIndex) {
                        Spacer(Modifier.height(RootToolsSpacing.sm))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(entry: HomeTimelineEntry) {
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.timestampMs))
    val label = when (entry.kind) {
        HomeTimelineKind.PERFORMANCE -> stringResource(R.string.home_timeline_performance)
        HomeTimelineKind.ROOT_ACTION -> stringResource(R.string.home_timeline_system)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(RootToolsSpacing.sm),
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                RootToolsStatusChip(
                    label = if (entry.success) stringResource(R.string.hub_status_ready) else stringResource(R.string.integrity_level_warning),
                    tone = if (entry.success) RootToolsStatusTone.Neutral else RootToolsStatusTone.Warning,
                )
            }
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun temperatureText(value: Float?): String =
    value?.let { stringResource(R.string.common_temperature_celsius, it) }
        ?: stringResource(R.string.common_dash)

private data class QuickAction(
    val labelRes: Int,
    val icon: ImageVector,
    val destination: RootToolsDestination,
)
