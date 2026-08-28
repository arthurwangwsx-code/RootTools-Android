package com.arthur.roottools.feature.performance.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arthur.roottools.R
import com.arthur.roottools.core.presentation.formatGHz
import com.arthur.roottools.core.presentation.labelRes
import com.arthur.roottools.core.ui.component.RootToolsDetailHeader
import com.arthur.roottools.core.ui.component.RootToolsErrorCard
import com.arthur.roottools.core.ui.component.RootToolsRiskBanner
import com.arthur.roottools.core.ui.component.RootToolsSectionHeader
import com.arthur.roottools.core.ui.component.RootToolsStatusChip
import com.arthur.roottools.core.ui.component.RootToolsTemperatureMetric
import com.arthur.roottools.core.ui.token.RootToolsRiskLevel
import com.arthur.roottools.core.ui.token.RootToolsStatusTone
import com.arthur.roottools.feature.performance.presentation.PerformanceUiState
import com.arthur.roottools.model.AdaptiveThermalReason
import com.arthur.roottools.model.CpuCluster
import com.arthur.roottools.model.DeviceSnapshot
import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.model.ThermalStage

@Composable
internal fun PerformanceScreen(
    state: PerformanceUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onModeSelected: (PerformanceMode) -> Unit,
    onReleaseCaps: () -> Unit,
) {
    val snapshot = state.snapshot
    val stage = state.adaptiveStage
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                RootToolsDetailHeader(
                    title = stringResource(R.string.tool_performance_title),
                    subtitle = stringResource(R.string.performance_detail_subtitle),
                    onBack = onBack,
                    loading = state.loading,
                    onRefresh = onRefresh,
                )
            }
            item { PerformanceHero(snapshot, state.mode, stage) }
            item {
                PerformanceModeCard(
                    selected = state.mode,
                    stage = stage,
                    busy = state.actionInProgress,
                    onModeSelected = onModeSelected,
                )
            }
            item { AdaptiveThermalCard(state.adaptiveStage, state.adaptiveReason) }
            item {
                RootToolsSectionHeader(
                    stringResource(R.string.performance_section_cpu_title),
                    stringResource(R.string.performance_section_cpu_subtitle),
                )
            }
            itemsIndexed(snapshot.cpuClusters) { index, cluster ->
                val cap = state.cpuCapStates.firstOrNull { it.policyId == cluster.policyId }
                CpuClusterCard(
                    index = index,
                    cluster = cluster,
                    isLast = index == snapshot.cpuClusters.lastIndex,
                    source = cap?.source?.let { stringResource(it.labelRes()) },
                )
            }
            if (state.cpuCapStates.any { it.ownedMaxKHz > 0L }) {
                item {
                    OutlinedButton(
                        onClick = onReleaseCaps,
                        enabled = !state.actionInProgress,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.performance_release_owned_caps))
                    }
                }
            }
            item {
                RootToolsSectionHeader(
                    stringResource(R.string.performance_history_title),
                    stringResource(R.string.performance_history_subtitle),
                )
            }
            item { CpuPolicyHistoryCard(state) }
            item {
                RootToolsRiskBanner(
                    title = stringResource(R.string.performance_safety_title),
                    detail = stringResource(R.string.performance_safety_body),
                    level = RootToolsRiskLevel.Safe,
                )
            }
            state.actionMessage?.let { message -> item { MessageCard(message) } }
            state.error?.let { message -> item { RootToolsErrorCard(message) } }
        }
    }
}

@Composable
private fun AdaptiveThermalCard(stage: ThermalStage, adaptiveReason: AdaptiveThermalReason) {
    val reason = when (adaptiveReason) {
        AdaptiveThermalReason.NORMAL -> R.string.performance_adaptive_reason_normal
        AdaptiveThermalReason.BACKGROUND_EFFICIENCY -> R.string.performance_adaptive_reason_background
        AdaptiveThermalReason.CHARGING_HEAT -> R.string.performance_adaptive_reason_charging
        AdaptiveThermalReason.BATTERY_TEMPERATURE -> R.string.performance_adaptive_reason_battery
        AdaptiveThermalReason.SKIN_TEMPERATURE -> R.string.performance_adaptive_reason_skin
        AdaptiveThermalReason.SYSTEM_THERMAL -> R.string.performance_adaptive_reason_system
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    Icons.Rounded.Thermostat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(0.04f))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.performance_adaptive_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.performance_adaptive_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RootToolsStatusChip(
                label = stringResource(
                    R.string.performance_adaptive_current,
                    stringResource(stage.labelRes()),
                ),
                tone = when (stage) {
                    ThermalStage.NORMAL -> RootToolsStatusTone.Success
                    ThermalStage.WARM -> RootToolsStatusTone.Warning
                    ThermalStage.MODERATE, ThermalStage.SEVERE -> RootToolsStatusTone.Danger
                },
            )
            Text(
                stringResource(reason),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.performance_adaptive_rule_interactive),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.performance_adaptive_rule_background),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.performance_adaptive_rule_upgrade),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PerformanceHero(snapshot: DeviceSnapshot, mode: PerformanceMode, stage: ThermalStage) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ),
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(targetState = mode, label = "performance-mode") { current ->
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(
                                R.string.performance_hero_mode_stage,
                                stringResource(current.labelRes()),
                                stringResource(stage.labelRes()),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.performance_thermal_status, snapshot.thermalStatus),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                RootToolsStatusChip(
                    label = stringResource(if (snapshot.rootAvailable) R.string.common_root else R.string.common_no_root),
                    tone = if (snapshot.rootAvailable) RootToolsStatusTone.Success else RootToolsStatusTone.Danger,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RootToolsTemperatureMetric(stringResource(R.string.dashboard_metric_ap), snapshot.apTempC, Modifier.weight(1f))
                RootToolsTemperatureMetric(stringResource(R.string.dashboard_metric_skin), snapshot.skinTempC, Modifier.weight(1f))
                RootToolsTemperatureMetric(stringResource(R.string.dashboard_metric_battery), snapshot.batteryTempC, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RootToolsStatusChip(
                    label = if (snapshot.thermalStatus == 0) {
                        stringResource(R.string.thermal_stage_normal)
                    } else {
                        stringResource(stage.labelRes())
                    },
                    tone = if (snapshot.thermalStatus == 0) RootToolsStatusTone.Success else RootToolsStatusTone.Warning,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.performance_system_thermal_kept),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PerformanceModeCard(
    selected: PerformanceMode,
    stage: ThermalStage,
    busy: Boolean,
    onModeSelected: (PerformanceMode) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icons.Rounded.Speed.let { androidx.compose.material3.Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(1.dp).weight(0.04f))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.performance_mode_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.performance_mode_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PerformanceMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selected == mode,
                        onClick = { onModeSelected(mode) },
                        enabled = !busy,
                        label = { Text(stringResource(mode.labelRes())) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            AnimatedContent(targetState = selected, label = "performance-mode-description") { mode ->
                Text(
                    when (mode) {
                        PerformanceMode.AUTO -> stringResource(
                            R.string.performance_mode_auto_description,
                            stringResource(stage.labelRes()),
                        )
                        PerformanceMode.COOL -> stringResource(R.string.performance_mode_cool_description)
                        PerformanceMode.PERFORMANCE -> stringResource(R.string.performance_mode_performance_description)
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
    index: Int,
    cluster: CpuCluster,
    isLast: Boolean,
    source: String?,
) {
    val icon = when {
        index == 0 -> Icons.Rounded.Memory
        isLast -> Icons.Rounded.Speed
        else -> Icons.Rounded.Security
    }
    val label = when {
        index == 0 -> stringResource(R.string.performance_cluster_efficiency)
        isLast -> stringResource(R.string.performance_cluster_prime)
        else -> stringResource(R.string.performance_cluster_performance)
    }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(0.04f))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.performance_cluster_cpu, cluster.relatedCpus, cluster.policyId),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    source?.let { stringResource(R.string.performance_limit_source, it) }
                        ?: stringResource(R.string.performance_limit_source_full),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatGHz(cluster.currentKHz), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.performance_upper_limit, formatGHz(cluster.scalingMaxKHz)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CpuPolicyHistoryCard(state: PerformanceUiState) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.cpuPolicyEvents.isEmpty()) {
                Text(stringResource(R.string.performance_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.cpuPolicyEvents.take(12).forEach { event ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            stringResource(R.string.performance_history_event_meta, event.type.name, relativeTimeLabel(event.timestampMs)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.weight(0.04f))
                        Text(
                            event.message,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun relativeTimeLabel(timestampMs: Long): String {
    val delta = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    return when {
        delta < 60_000L -> stringResource(R.string.time_seconds_ago, delta / 1_000L)
        delta < 60 * 60_000L -> stringResource(R.string.time_minutes_ago, delta / 60_000L)
        delta < 24 * 60 * 60_000L -> stringResource(R.string.time_hours_ago, delta / (60 * 60_000L))
        else -> stringResource(R.string.time_days_ago, delta / (24 * 60 * 60_000L))
    }
}

@Composable
private fun MessageCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(
            message,
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
