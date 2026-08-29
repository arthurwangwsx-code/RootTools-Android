package com.aibox.backgroundserver.ui.power

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aibox.backgroundserver.domain.PowerSettings
import com.aibox.backgroundserver.domain.RootStatus
import com.aibox.backgroundserver.domain.RuntimeMetrics
import com.aibox.backgroundserver.ui.components.CardDivider
import com.aibox.backgroundserver.ui.components.PageScaffold
import com.aibox.backgroundserver.ui.components.SectionCard
import com.aibox.backgroundserver.ui.components.SettingRow
import com.aibox.backgroundserver.ui.components.ToggleRow
import java.util.Locale

@Composable
fun PowerScreen(
    rootStatus: RootStatus,
    settings: PowerSettings,
    metrics: RuntimeMetrics,
    onBack: () -> Unit,
    onScreenWake: () -> Unit,
    onScreenOffWork: (Boolean) -> Unit,
    onRestoreAfterBoot: (Boolean) -> Unit,
    onSleepNow: () -> Unit,
) {
    PageScaffold(title = "电源管理", onBack = onBack) { contentModifier ->
        LazyColumn(
            modifier = contentModifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                RuntimeSummary(metrics)
            }
            item {
                Text("工作模式", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 4.dp))
                SectionCard {
                    ToggleRow(
                        title = "息屏保持工作",
                        subtitle = "保持 CPU/网络后台运行，显示面板可正常关闭",
                        checked = settings.screenOffWorkEnabled || metrics.running,
                        enabled = rootStatus.available,
                        onCheckedChange = onScreenOffWork,
                    )
                    CardDivider()
                    ToggleRow(
                        title = "开机恢复后台工作",
                        subtitle = "重启完成后，如果息屏工作原本处于启用状态，则自动恢复前台服务",
                        checked = settings.restoreAfterBoot,
                        enabled = rootStatus.available,
                        onCheckedChange = onRestoreAfterBoot,
                    )
                    CardDivider()
                    SettingRow(
                        title = "息屏与唤醒",
                        subtitle = "双击唤醒、立即息屏及唤醒测试",
                        onClick = onScreenWake,
                    )
                }
            }
            item {
                SystemLoadSummary(metrics)
            }
            item {
                SectionCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("快速操作", fontWeight = FontWeight.SemiBold)
                        Text("建议先开启“息屏保持工作”，再关闭显示屏。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        Button(onClick = onSleepNow, enabled = rootStatus.available, modifier = Modifier.fillMaxWidth()) {
                            androidx.compose.material3.Icon(Icons.Rounded.Bedtime, contentDescription = null)
                            Text(" 立即息屏")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeSummary(metrics: RuntimeMetrics) {
    SectionCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(if (metrics.running) "后台工作中" else "当前空闲", fontWeight = FontWeight.SemiBold)
                    Text(formatDuration(metrics.runtimeMillis), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Text(if (metrics.running) "● ACTIVE" else "● IDLE", color = if (metrics.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("瞬时功耗", metrics.instantaneousWatts?.let { String.format(Locale.US, "%.2f W", it) } ?: "--")
                Metric("累计耗电", String.format(Locale.US, "%.3f Wh", metrics.accumulatedWh))
                Metric("CPU 负载", metrics.cpuLoadPercent?.let { String.format(Locale.US, "%.0f%%", it) } ?: "--")
                Metric("温度", metrics.temperatureCelsius?.let { String.format(Locale.US, "%.1f°C", it) } ?: "--")
            }
            Text(
                if (metrics.batteryCharging) "当前处于充电/外部供电状态；这里显示的是电池管理框架侧瞬时功率估算，不等同于插座输入功率。"
                else "功耗根据 Android 电池电流与电压采样估算，用于比较不同后台工作模式。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun SystemLoadSummary(metrics: RuntimeMetrics) {
    SectionCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("系统负载", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("CPU 使用率", metrics.cpuLoadPercent?.let { String.format(Locale.US, "%.0f%%", it) } ?: "--")
                Metric("内存使用", metrics.memoryUsedPercent?.let { String.format(Locale.US, "%.0f%%", it) } ?: "--")
                Metric("Load 1m", metrics.loadAverage1?.let { String.format(Locale.US, "%.2f", it) } ?: "--")
                Metric("Load 5m", metrics.loadAverage5?.let { String.format(Locale.US, "%.2f", it) } ?: "--")
            }
            Text(
                "Load 1/5/15: ${formatLoad(metrics.loadAverage1)} / ${formatLoad(metrics.loadAverage5)} / ${formatLoad(metrics.loadAverage15)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                "系统网络累计：↓ ${formatBytes(metrics.totalRxBytes)}  ↑ ${formatBytes(metrics.totalTxBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(value, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, remaining)
}

private fun formatLoad(value: Double?): String = value?.let { String.format(Locale.US, "%.2f", it) } ?: "--"

private fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "--"
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
