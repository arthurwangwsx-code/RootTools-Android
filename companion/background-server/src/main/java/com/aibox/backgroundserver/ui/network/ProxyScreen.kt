package com.aibox.backgroundserver.ui.network

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aibox.backgroundserver.domain.NetworkCapabilities
import com.aibox.backgroundserver.domain.NetworkSnapshot
import com.aibox.backgroundserver.domain.TunnelRuntimeState
import com.aibox.backgroundserver.domain.WireGuardServerState
import com.aibox.backgroundserver.ui.components.CardDivider
import com.aibox.backgroundserver.ui.components.PageScaffold
import com.aibox.backgroundserver.ui.components.SectionCard
import com.aibox.backgroundserver.ui.components.SettingRow
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

@Composable
fun ProxyScreen(
    capabilities: NetworkCapabilities,
    network: NetworkSnapshot,
    wireGuard: WireGuardServerState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    vpnPermissionIntent: () -> Intent?,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    val vpnPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) onStart()
    }
    val running = wireGuard.runtimeState == TunnelRuntimeState.RUNNING
    val busy = wireGuard.runtimeState == TunnelRuntimeState.STARTING || wireGuard.runtimeState == TunnelRuntimeState.STOPPING

    fun requestStart() {
        val intent = vpnPermissionIntent()
        if (intent == null) onStart() else vpnPermissionLauncher.launch(intent)
    }

    PageScaffold(title = "后台代理", onBack = onBack) { contentModifier ->
        LazyColumn(
            modifier = contentModifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("WireGuard Server", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (wireGuard.runtimeState) {
                                TunnelRuntimeState.STOPPED -> "已停止"
                                TunnelRuntimeState.STARTING -> "正在启动"
                                TunnelRuntimeState.RUNNING -> "正在监听 ${network.wifiLikeIpv4 ?: "LAN-IP"}:${wireGuard.listenPort}"
                                TunnelRuntimeState.STOPPING -> "正在停止"
                                TunnelRuntimeState.ERROR -> "启动失败"
                            },
                            color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        )
                        if (wireGuard.error != null) {
                            Text(wireGuard.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (running) {
                                OutlinedButton(onClick = onStop, enabled = !busy) { Text("停止") }
                            } else {
                                Button(
                                    onClick = ::requestStart,
                                    enabled = !busy && capabilities.tunAvailable && capabilities.iptablesAvailable,
                                ) { Text(if (wireGuard.requiresVpnPermission) "授权并启动" else "启动服务") }
                            }
                            OutlinedButton(onClick = onRefresh, enabled = !busy) { Text("重新检测") }
                        }
                    }
                }
            }

            item {
                Text("运行能力", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 4.dp))
                SectionCard {
                    SettingRow("推荐后端", capabilities.recommendedBackend)
                    CardDivider()
                    SettingRow("TUN", if (capabilities.tunAvailable) "可用" else "缺失")
                    CardDivider()
                    SettingRow("iptables / NAT", if (capabilities.iptablesAvailable) "可用" else "缺失")
                    CardDivider()
                    SettingRow("内核 WireGuard", if (capabilities.kernelWireGuardAvailable) "可用" else "当前 ROM 未提供")
                    CardDivider()
                    SettingRow("IPv4 Forwarding", if (capabilities.ipv4ForwardingEnabled || running) "已开启/由服务管理" else "当前关闭，启动时开启")
                }
            }

            item {
                Text("服务参数", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 4.dp))
                SectionCard {
                    SettingRow("服务端隧道地址", wireGuard.tunnelAddress)
                    CardDivider()
                    SettingRow("测试客户端地址", wireGuard.peerAddress)
                    CardDivider()
                    SettingRow("监听端口", "UDP ${wireGuard.listenPort}")
                    CardDivider()
                    SettingRow("出口接口", wireGuard.egressInterface)
                    CardDivider()
                    SettingRow("服务端公钥", wireGuard.serverPublicKey)
                }
            }

            item {
                Text("流量", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 4.dp))
                SectionCard {
                    SettingRow("接收", formatBytes(wireGuard.rxBytes))
                    CardDivider()
                    SettingRow("发送", formatBytes(wireGuard.txBytes))
                }
            }

            item {
                Text("LAN 测试客户端", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 4.dp))
                SectionCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("扫码连接", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (running) {
                                "在 iPhone 的 Shadowrocket 首页点击左上角扫码按钮，扫描下方二维码即可导入当前局域网 WireGuard 节点。"
                            } else {
                                "二维码已经按当前局域网地址生成；请先启动 WireGuard Server，再用 Shadowrocket 扫描。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        QrCode(
                            content = wireGuard.clientConfig,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(292.dp),
                        )
                        Text(
                            "Endpoint: ${network.wifiLikeIpv4 ?: "LAN-IP"}:${wireGuard.listenPort}",
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "当前二维码内含测试客户端私钥，仅用于这一台测试客户端。正式远程接入会改成独立 Peer/吊销管理。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        SelectionContainer {
                            Text(
                                wireGuard.clientConfig,
                                modifier = Modifier.fillMaxWidth(),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("WireGuard client config", wireGuard.clientConfig))
                        }) { Text("复制客户端配置") }
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCode(content: String, modifier: Modifier = Modifier) {
    val bitmap = remember(content) { createQrBitmap(content) }
    Box(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "WireGuard 客户端配置二维码",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun createQrBitmap(content: String, size: Int = 900): Bitmap {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        ),
    )
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until size) {
            for (x in 0 until size) {
                setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
