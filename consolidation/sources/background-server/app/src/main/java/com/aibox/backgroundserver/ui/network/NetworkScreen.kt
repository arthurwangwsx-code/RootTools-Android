package com.aibox.backgroundserver.ui.network

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aibox.backgroundserver.domain.NetworkSnapshot
import com.aibox.backgroundserver.domain.RootStatus
import com.aibox.backgroundserver.ui.components.CardDivider
import com.aibox.backgroundserver.ui.components.PageScaffold
import com.aibox.backgroundserver.ui.components.SectionCard
import com.aibox.backgroundserver.ui.components.SettingRow

@Composable
fun NetworkScreen(
    rootStatus: RootStatus,
    snapshot: NetworkSnapshot,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onProxy: () -> Unit,
) {
    val context = LocalContext.current
    fun hasLanPermission(): Boolean = Build.VERSION.SDK_INT < 37 ||
        ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED
    var lanPermissionGranted by remember { mutableStateOf(hasLanPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        lanPermissionGranted = granted
        if (granted) onRefresh()
    }

    PageScaffold(title = "网络与连接", onBack = onBack) { contentModifier ->
        LazyColumn(
            modifier = contentModifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("局域网地址", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text(snapshot.wifiLikeIpv4 ?: "未检测到 IPv4", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Root: ${if (rootStatus.available) "已就绪" else "未授权"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (!lanPermissionGranted) {
                item {
                    SectionCard {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("局域网访问权限", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Android 17+ 会阻止未授权应用接受或发起局域网连接。后台代理服务需要此权限。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Button(onClick = { permissionLauncher.launch(ACCESS_LOCAL_NETWORK_PERMISSION) }) {
                                Text("授权局域网访问")
                            }
                        }
                    }
                }
            }
            item {
                Text("网络接口", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 4.dp))
                SectionCard {
                    SettingRow("主接口", snapshot.primaryInterface ?: "未检测到")
                    CardDivider()
                    SettingRow("LAN CIDR", snapshot.primaryCidr ?: snapshot.wifiLikeIpv4 ?: "未检测到")
                    CardDivider()
                    SettingRow("默认网关", snapshot.gateway ?: "未检测到")
                    CardDivider()
                    SettingRow("DNS", snapshot.dnsServers.joinToString().ifBlank { "未检测到" })
                    if (snapshot.addresses.isNotEmpty()) CardDivider()
                    if (snapshot.addresses.isEmpty()) {
                        SettingRow("暂无可用地址", "确认 Wi‑Fi 或以太网已经连接")
                    } else {
                        snapshot.addresses.forEachIndexed { index, address ->
                            SettingRow(
                                title = address.address,
                                subtitle = "${address.interfaceName} · ${if (address.ipv6) "IPv6" else "IPv4"}",
                            )
                            if (index != snapshot.addresses.lastIndex) CardDivider()
                        }
                    }
                }
            }
            item {
                Button(onClick = onRefresh) { Text("刷新网络信息") }
            }
            item {
                SectionCard {
                    SettingRow(
                        title = "后台代理",
                        subtitle = "WireGuard Server · LAN 联通 · 路由/NAT",
                        onClick = onProxy,
                    )
                }
            }
        }
    }
}

private const val ACCESS_LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
