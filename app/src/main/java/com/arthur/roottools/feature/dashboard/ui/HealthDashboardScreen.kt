package com.arthur.roottools.feature.dashboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arthur.roottools.R
import com.arthur.roottools.core.ui.component.RootToolsDetailHeader
import com.arthur.roottools.core.ui.component.RootToolsErrorCard
import com.arthur.roottools.core.ui.component.RootToolsKeyValueRow
import com.arthur.roottools.core.ui.component.RootToolsSectionHeader
import com.arthur.roottools.core.presentation.formatGHz
import com.arthur.roottools.core.presentation.formatMemoryKb
import com.arthur.roottools.core.presentation.formatUptime
import com.arthur.roottools.feature.dashboard.presentation.frequencyBins
import com.arthur.roottools.feature.dashboard.presentation.HealthDashboardUiState
import com.arthur.roottools.feature.dashboard.presentation.rangeText
import com.arthur.roottools.model.DeviceHealthSnapshot
import com.arthur.roottools.model.HealthHistoryPoint
import com.arthur.roottools.model.MemoryPressureStatus
import com.arthur.roottools.model.ProcessHealth

@Composable
internal fun HealthDashboardScreen(
    state: HealthDashboardUiState,
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
                RootToolsDetailHeader(
                    title = stringResource(R.string.tool_dashboard_title),
                    subtitle = stringResource(R.string.dashboard_sampling_subtitle, state.detailSamplingSeconds),
                    onBack = onBack,
                    loading = state.loading,
                    onRefresh = onRefresh,
                )
            }
            item { SamplingIntervalCard(state.detailSamplingSeconds, onSamplingSeconds) }
            item { HealthOverviewCard(health, history) }
            item { DailyHealthHistoryCard(dailyHistory) }
            item {
                RootToolsSectionHeader(
                    stringResource(R.string.dashboard_section_cpu_load),
                    stringResource(R.string.dashboard_section_cpu_load_subtitle),
                )
            }
            item { CpuHealthCard(health) }
            item { SchedulerHealthCard(health) }
            item { FrequencyDistributionCard(health, history) }
            item {
                RootToolsSectionHeader(
                    stringResource(R.string.dashboard_section_memory_zram),
                    stringResource(R.string.dashboard_section_memory_zram_subtitle),
                )
            }
            item { MemoryHealthCard(health) }
            item { TopMemoryCard(health) }
            item { LmkHealthCard(health) }
            item {
                RootToolsSectionHeader(
                    stringResource(R.string.dashboard_section_thermal_battery),
                    stringResource(R.string.dashboard_section_thermal_battery_subtitle),
                )
            }
            item { ThermalBatteryCard(health) }
            item {
                RootToolsSectionHeader(
                    stringResource(R.string.dashboard_section_process_system),
                    stringResource(R.string.dashboard_section_process_system_subtitle),
                )
            }
            item { ProcessHealthCard(health) }
            if (!health.rootAvailable) {
                item { RootToolsErrorCard(stringResource(R.string.dashboard_root_required)) }
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
                HealthMetric(
                    stringResource(R.string.dashboard_metric_cpu),
                    stringResource(R.string.dashboard_percent_integer, health.cpuUsagePercent),
                    stringResource(R.string.dashboard_cpu_idle, health.cpuIdlePercent),
                    Modifier.weight(1f),
                )
                HealthMetric(
                    stringResource(R.string.dashboard_metric_memory),
                    stringResource(R.string.dashboard_gb_one_decimal, health.memory.availableKb / 1_048_576f),
                    stringResource(R.string.dashboard_available),
                    Modifier.weight(1f),
                )
                HealthMetric(
                    stringResource(R.string.dashboard_metric_ap),
                    health.thermal.apC?.let { stringResource(R.string.dashboard_temperature_one_decimal, it) }
                        ?: stringResource(R.string.common_dash),
                    stringResource(R.string.dashboard_thermal_status, health.thermal.status),
                    Modifier.weight(1f),
                )
            }
            if (history.size >= 2) {
                Text(
                    stringResource(R.string.dashboard_recent_status),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HistorySparkline(history)
            }
            health.abnormalRootShell?.let { abnormal ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                    Text(
                        stringResource(R.string.dashboard_abnormal_root_shell, abnormal.pid, abnormal.cpuPercent),
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
internal fun SamplingIntervalCard(selectedSeconds: Int, onSelected: (Int) -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.dashboard_sampling_title), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.dashboard_sampling_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.dashboard_seconds_short, selectedSeconds),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 5).forEach { seconds ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = selectedSeconds == seconds,
                        onClick = { onSelected(seconds) },
                        label = {
                            Text(
                                if (seconds == 1) {
                                    stringResource(R.string.dashboard_one_second_experimental)
                                } else {
                                    stringResource(R.string.dashboard_seconds_short, seconds)
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun DailyHealthHistoryCard(history: List<HealthHistoryPoint>) {
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(stringResource(R.string.dashboard_history_24h_title), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.dashboard_history_24h_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.dashboard_history_count, history.size),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (history.size >= 2) {
                HistorySparkline(history, maxPoints = 288)
                RootToolsKeyValueRow(
                    stringResource(R.string.dashboard_cpu_peak),
                    peakCpu?.let { stringResource(R.string.dashboard_percent_integer, it) }
                        ?: stringResource(R.string.common_dash),
                )
                RootToolsKeyValueRow(
                    stringResource(R.string.dashboard_mem_available_min),
                    minMem?.let { stringResource(R.string.dashboard_gb_one_decimal, it / 1_048_576f) }
                        ?: stringResource(R.string.common_dash),
                )
                RootToolsKeyValueRow(stringResource(R.string.dashboard_ap_min_max), rangeText(ap))
                RootToolsKeyValueRow(stringResource(R.string.dashboard_skin_min_max), rangeText(skin))
                RootToolsKeyValueRow(
                    stringResource(R.string.dashboard_battery_min_max),
                    if (batteries.isEmpty()) {
                        stringResource(R.string.common_dash)
                    } else {
                        stringResource(R.string.dashboard_battery_range, batteries.minOrNull() ?: 0, batteries.maxOrNull() ?: 0)
                    },
                )
            } else {
                Text(
                    stringResource(R.string.dashboard_history_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Text(stringResource(R.string.dashboard_cpu_usage, health.cpuUsagePercent), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.dashboard_load, health.load1, health.load5, health.load15),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            health.cpuClusters.forEachIndexed { index, cluster ->
                if (index > 0) HorizontalDivider()
                val title = when {
                    index == 0 -> stringResource(R.string.dashboard_core_efficiency)
                    index == health.cpuClusters.lastIndex && index >= 2 -> stringResource(R.string.dashboard_core_prime)
                    else -> stringResource(R.string.dashboard_core_performance)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(stringResource(R.string.dashboard_cluster_cpu, title, cluster.relatedCpus), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(
                                R.string.dashboard_governor_util,
                                cluster.governor.ifBlank { stringResource(R.string.common_dash) },
                                cluster.utilizationPercent,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatGHz(cluster.currentKHz), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(
                                R.string.dashboard_max_hw,
                                formatGHz(cluster.scalingMaxKHz),
                                formatGHz(cluster.hardwareMaxKHz),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
            Text(stringResource(R.string.dashboard_scheduler_title), fontWeight = FontWeight.Bold)
            if (health.scheduler.groups.isEmpty()) {
                Text(stringResource(R.string.dashboard_scheduler_waiting), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                health.scheduler.groups.forEachIndexed { index, group ->
                    if (index > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(group.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(
                                    R.string.dashboard_cpu_group,
                                    group.cpus.ifBlank { stringResource(R.string.common_dash) },
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            if (group.uclampMin != null || group.uclampMax != null) {
                                stringResource(
                                    R.string.dashboard_uclamp_range,
                                    group.uclampMin ?: stringResource(R.string.common_dash),
                                    group.uclampMax ?: stringResource(R.string.common_dash),
                                )
                            } else {
                                stringResource(R.string.dashboard_cpuset)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.dashboard_scheduler_read_only),
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
            Text(stringResource(R.string.dashboard_frequency_title), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.dashboard_frequency_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (history.none { it.clusterCurrentKHz.isNotEmpty() }) {
                Text(stringResource(R.string.dashboard_frequency_waiting), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                health.cpuClusters.forEachIndexed { index, cluster ->
                    val name = when {
                        index == 0 -> stringResource(R.string.dashboard_cluster_efficiency)
                        index == health.cpuClusters.lastIndex && index >= 2 -> stringResource(R.string.dashboard_cluster_prime)
                        else -> stringResource(R.string.dashboard_cluster_big)
                    }
                    val five = frequencyBins(history, cluster.policyId, cluster.hardwareMaxKHz, 5 * 60_000L)
                    val thirty = frequencyBins(history, cluster.policyId, cluster.hardwareMaxKHz, 30 * 60_000L)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.dashboard_cluster_cpu, name, cluster.relatedCpus), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.dashboard_frequency_window, stringResource(R.string.dashboard_window_5m), five.asLabel()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.dashboard_frequency_window, stringResource(R.string.dashboard_window_30m), thirty.asLabel()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.dashboard_memory_status_available, memory.status.displayName, memory.availableKb / 1_048_576f),
                    fontWeight = FontWeight.Bold,
                )
                Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor))
            }
            RootToolsKeyValueRow(stringResource(R.string.dashboard_mem_total), stringResource(R.string.dashboard_gb_one_decimal, memory.totalKb / 1_048_576f))
            RootToolsKeyValueRow(
                stringResource(R.string.dashboard_cached_anon),
                stringResource(R.string.dashboard_gb_pair, memory.cachedKb / 1_048_576f, memory.anonKb / 1_048_576f),
            )
            RootToolsKeyValueRow(
                stringResource(R.string.dashboard_swap),
                stringResource(R.string.dashboard_gb_pair, memory.swapUsedKb / 1_048_576f, memory.swapTotalKb / 1_048_576f),
            )
            RootToolsKeyValueRow(
                stringResource(R.string.dashboard_zram_compression),
                if (memory.compressionRatio > 0f) {
                    stringResource(R.string.dashboard_compression_ratio, memory.compressionRatio)
                } else {
                    stringResource(R.string.common_dash)
                },
            )
            RootToolsKeyValueRow(
                stringResource(R.string.dashboard_memory_psi),
                stringResource(R.string.dashboard_psi_pair, memory.pressure.someAvg10, memory.pressure.fullAvg10),
            )
            RootToolsKeyValueRow(
                stringResource(R.string.dashboard_io_psi),
                stringResource(R.string.dashboard_psi_pair, health.ioPressure.someAvg10, health.ioPressure.fullAvg10),
            )
            if (memory.swapUsedRatio >= 0.8f && memory.status == MemoryPressureStatus.HEALTHY) {
                Text(
                    stringResource(R.string.dashboard_swap_not_pressure),
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
            Text(stringResource(R.string.dashboard_top_memory_title), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.dashboard_top_memory_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (health.topMemoryProcesses.isEmpty()) {
                Text(stringResource(R.string.dashboard_top_memory_waiting), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                health.topMemoryProcesses.forEach { process ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(process.processName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(R.string.dashboard_pid_user, process.pid, process.user),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.dashboard_rss, formatMemoryKb(process.rssKb)), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.dashboard_pss, formatMemoryKb(process.pssKb)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text(stringResource(R.string.dashboard_lmk_title), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.dashboard_lmk_count, health.lmk.chimeraKillCount),
                    color = if (health.lmk.chimeraKillCount > 0) Color(0xFFFFB74D) else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            RootToolsKeyValueRow(stringResource(R.string.dashboard_recent_am_kill), health.lmk.recentKillCount.toString())
            if (health.lmk.config.isNotEmpty()) {
                health.lmk.config.entries.take(6).forEach { (key, value) -> RootToolsKeyValueRow(key, value) }
            } else {
                Text(
                    stringResource(R.string.dashboard_lmk_no_static_config),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            health.lmk.recentReasons.takeLast(3).forEach { reason ->
                Text(
                    reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
                HealthMetric(
                    stringResource(R.string.dashboard_metric_ap),
                    thermal.apC?.let { stringResource(R.string.dashboard_temperature_one_decimal, it) } ?: stringResource(R.string.common_dash),
                    stringResource(R.string.dashboard_status_value, thermal.status),
                    Modifier.weight(1f),
                )
                HealthMetric(
                    stringResource(R.string.dashboard_metric_skin),
                    thermal.skinC?.let { stringResource(R.string.dashboard_temperature_one_decimal, it) } ?: stringResource(R.string.common_dash),
                    stringResource(R.string.dashboard_surface),
                    Modifier.weight(1f),
                )
                HealthMetric(
                    stringResource(R.string.dashboard_metric_battery),
                    thermal.batteryC?.let { stringResource(R.string.dashboard_temperature_one_decimal, it) } ?: stringResource(R.string.common_dash),
                    stringResource(R.string.dashboard_battery_percent, battery.level ?: 0),
                    Modifier.weight(1f),
                )
            }
            RootToolsKeyValueRow(
                stringResource(R.string.dashboard_usb_pathm),
                stringResource(
                    R.string.dashboard_temperature_pair,
                    thermal.usbC?.let { stringResource(R.string.dashboard_temperature_one_decimal, it) } ?: stringResource(R.string.common_dash),
                    thermal.pathmC?.let { stringResource(R.string.dashboard_temperature_one_decimal, it) } ?: stringResource(R.string.common_dash),
                ),
            )
            RootToolsKeyValueRow(
                stringResource(R.string.dashboard_charging),
                stringResource(if (battery.charging) R.string.dashboard_charging_yes else R.string.dashboard_charging_no),
            )
            RootToolsKeyValueRow(
                stringResource(R.string.dashboard_voltage_current),
                stringResource(R.string.dashboard_voltage_current_value, battery.voltageMv ?: 0, battery.currentMa ?: 0),
            )
            RootToolsKeyValueRow(
                stringResource(R.string.dashboard_battery_protection),
                if (battery.protectionEnabled) {
                    stringResource(R.string.dashboard_battery_protection_on, battery.protectionThreshold ?: 80)
                } else {
                    stringResource(R.string.dashboard_off)
                },
            )
        }
    }
}

@Composable
private fun ProcessHealthCard(health: DeviceHealthSnapshot) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RootToolsKeyValueRow(stringResource(R.string.dashboard_uptime), formatUptime(health.uptimeSeconds))
            RootToolsKeyValueRow(stringResource(R.string.dashboard_processes), health.processCount.toString())
            HorizontalDivider()
            if (health.topProcesses.isEmpty()) {
                Text(stringResource(R.string.dashboard_top_process_waiting), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(
                stringResource(R.string.dashboard_process_meta, process.pid, process.user, process.rss),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.dashboard_percent_one_decimal, process.cpuPercent),
            fontWeight = FontWeight.Bold,
            color = if (process.isRootShell && process.cpuPercent >= 50f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}
