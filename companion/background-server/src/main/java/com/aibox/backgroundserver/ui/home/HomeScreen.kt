package com.aibox.backgroundserver.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aibox.backgroundserver.domain.NetworkSnapshot
import com.aibox.backgroundserver.domain.RootStatus
import com.aibox.backgroundserver.domain.RuntimeMetrics
import com.aibox.backgroundserver.domain.TunnelRuntimeState
import com.aibox.backgroundserver.domain.WireGuardServerState
import com.aibox.backgroundserver.ui.components.CardDivider
import com.aibox.backgroundserver.ui.components.PageScaffold
import com.aibox.backgroundserver.ui.components.SectionCard
import com.aibox.backgroundserver.ui.components.SettingRow

@Composable
fun HomeScreen(
    rootStatus: RootStatus,
    metrics: RuntimeMetrics,
    network: NetworkSnapshot,
    wireGuard: WireGuardServerState,
    onPower: () -> Unit,
    onNetwork: () -> Unit,
    onProxy: () -> Unit,
) {
    PageScaffold(title = "Background Server") { contentModifier ->
        LazyColumn(
            modifier = contentModifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionCard {
                    Row(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (rootStatus.available) "设备控制已就绪" else "等待 Root 授权",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                rootStatus.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        Text(
                            if (metrics.running) "运行中" else "空闲",
                            color = if (metrics.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            item {
                Text("管理", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 4.dp))
                SectionCard {
                    SettingRow(
                        title = "电源管理",
                        subtitle = if (metrics.running) "息屏后台工作正在运行" else "息屏、唤醒与后台保活",
                        onClick = onPower,
                        trailing = { Icon(Icons.Rounded.BatterySaver, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    )
                    CardDivider()
                    SettingRow(
                        title = "网络与连接",
                        subtitle = network.wifiLikeIpv4 ?: "查看局域网 IP 与接口",
                        onClick = onNetwork,
                        trailing = { Icon(Icons.Rounded.Lan, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    )
                }
            }
            item {
                Text("服务", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 4.dp))
                SectionCard {
                    SettingRow(
                        title = "后台代理",
                        subtitle = when (wireGuard.runtimeState) {
                            TunnelRuntimeState.RUNNING -> "运行中 · UDP ${wireGuard.listenPort} · ↓ ${formatBytes(wireGuard.rxBytes)} / ↑ ${formatBytes(wireGuard.txBytes)}"
                            TunnelRuntimeState.STARTING -> "WireGuard 正在启动"
                            TunnelRuntimeState.ERROR -> "WireGuard 异常 · 点击查看"
                            else -> "WireGuard · 局域网验证 · 路由/NAT"
                        },
                        onClick = onProxy,
                        trailing = { Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
