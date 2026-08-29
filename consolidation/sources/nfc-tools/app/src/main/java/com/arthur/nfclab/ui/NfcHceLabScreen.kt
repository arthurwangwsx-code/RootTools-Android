package com.arthur.nfclab.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arthur.nfclab.domain.NfcOperatingMode
import com.arthur.nfclab.domain.ProvisioningRequirementState
import com.arthur.nfclab.domain.ProvisioningRoute
import com.arthur.nfclab.domain.SimulationRoute
import com.arthur.nfclab.domain.SimulationSupport
import com.arthur.nfclab.hce.IsoDepLabProfiles
import com.arthur.nfclab.hce.LabApduProtocol
import com.arthur.nfclab.platform.simulation.SimulationCapabilityAnalyzer

@Composable
internal fun HceLabScreen(
    contentPadding: PaddingValues,
    state: NfcToolsUiState,
    onModeChange: (NfcOperatingMode) -> Unit,
    onPayloadChange: (String) -> Unit,
    onOpenWallet: (providerId: String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val scannedCard = state.lastSnapshot
    val scannedProduct = scannedCard?.details?.get("NXP product")
    val scannedIsoDep = scannedCard?.technologies?.any { it.substringAfterLast('.') == "IsoDep" } == true
    val simulationReport = SimulationCapabilityAnalyzer.analyze(
        snapshot = scannedCard,
        profile = state.deviceProfile,
        supportsHostHce = state.supportsHce,
        provisioning = state.provisioningCapability,
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CreditCard, contentDescription = null, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("ISO-DEP 模拟卡", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                if (state.operatingMode == NfcOperatingMode.HCE) "模拟已启动，等待外部 Reader" else "当前未启动",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = {
                            onModeChange(
                                if (state.operatingMode == NfcOperatingMode.HCE) NfcOperatingMode.DEFAULT
                                else NfcOperatingMode.HCE,
                            )
                        },
                        enabled = state.supportsHce,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.operatingMode == NfcOperatingMode.HCE) "停止模拟卡" else "启动实验模拟卡")
                    }
                }
            }
        }

        item {
            SectionCard("最近实体卡与可模拟范围") {
                if (scannedCard == null) {
                    Text(
                        "还没有最近扫描记录。先到“读卡”页识别一张卡，这里会自动判断 Android HCE 能模拟到哪一层。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    DetailRow("最近识别", scannedProduct ?: scannedCard.technologies.joinToString(" · "))
                    DetailRow(
                        "HCE 可验证范围",
                        if (scannedIsoDep) "ISO-DEP 应用层 / APDU 兼容性" else "当前卡型不属于 Android HostApduService 路径",
                    )
                    DetailRow("实体卡等价复制", "不支持")
                    Text(
                        if (scannedProduct?.contains("DESFire", ignoreCase = true) == true) {
                            "这张卡已识别为 $scannedProduct。HostApduService 可以做 ISO-DEP/APDU 实验模拟，但 Android 应用不能控制实体卡的 UID、ATQA、SAK，也不会从公开扫描结果得到 DESFire 应用密钥、受保护数据或卡内安全状态，因此不能把这次扫描直接变成可刷原门禁的副本。"
                        } else {
                            "这里会使用独立的 synthetic 测试身份进行模拟，不会把扫描到的实体卡身份、密钥或受保护数据复制进 HCE。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SectionCard("当前可用的模拟路径") {
                simulationReport.routes.forEachIndexed { index, route ->
                    Text("${index + 1}. ${route.title}", fontWeight = FontWeight.SemiBold)
                    DetailRow("状态", simulationSupportLabel(route.support))
                    Text(
                        route.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (route.cardTitles.isNotEmpty()) {
                        Text(
                            route.cardTitles.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (route.route == SimulationRoute.OEM_OFF_HOST && route.managementProviderId != null) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { onOpenWallet(route.managementProviderId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("在${route.sourceLabel ?: "系统钱包"}中管理 off-host 卡")
                        }
                    }
                    if (index != simulationReport.routes.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }
        }

        item {
            SectionCard("安全卡 Provisioning") {
                val provisioning = state.provisioningCapability
                if (provisioning == null) {
                    Text(
                        "正在等待设备 Provisioning 能力分析。这里会区分官方钱包、合作方 TSM 与 Direct eSE 三条路径。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    provisioning.routes.forEachIndexed { index, route ->
                        Text("${index + 1}. ${route.title}", fontWeight = FontWeight.SemiBold)
                        DetailRow("准备状态", provisioningReadinessLabel(route.readiness))
                        Text(
                            route.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        route.requirements.firstOrNull { requirement ->
                            requirement.state != ProvisioningRequirementState.SATISFIED &&
                                requirement.state != ProvisioningRequirementState.ACTION_AVAILABLE
                        }?.let { missing ->
                            Text(
                                "下一条件：${missing.title} · ${provisioningRequirementStateLabel(missing.state)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (route.route == ProvisioningRoute.OEM_WALLET && route.providerId != null) {
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { onOpenWallet(route.providerId) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("打开官方 Provisioning 入口")
                            }
                        }
                        if (index != provisioning.routes.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                    if (provisioning.nextSteps.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("当前下一步", fontWeight = FontWeight.SemiBold)
                        provisioning.nextSteps.forEach { step ->
                            Text(
                                "• $step",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard("当前模拟卡配置") {
                Text(
                    IsoDepLabProfiles.default.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Synthetic profile · 独立于刚扫描的实体门禁卡",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text("AID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectionContainer {
                        Text(
                            LabApduProtocol.AID,
                            modifier = Modifier.weight(1f),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    IconButton(onClick = { clipboard.setText(AnnotatedString(LabApduProtocol.AID)) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "复制 AID")
                    }
                }
                Text(
                    "外部 Reader 先 SELECT 上述 AID，再发送 80 CA 00 00 00，即可读取测试 payload。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.hcePayload,
                    onValueChange = onPayloadChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模拟卡 payload") },
                    supportingText = { Text("用于自有 Reader / 实验环境；不会从实体卡自动复制认证材料。") },
                    minLines = 2,
                )
            }
        }

        item {
            SectionCard("模拟能力分层") {
                Text(
                    "目标：${simulationReport.targetProduct ?: simulationReport.targetTechnology}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                simulationReport.layers.forEach { layer ->
                    DetailRow(layer.title, simulationSupportLabel(layer.support))
                    Text(
                        layer.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (layer.evidence.isNotEmpty()) {
                        Text(
                            "证据：${layer.evidence.joinToString(" · ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (simulationReport.blockers.isNotEmpty()) {
                    Text("当前关键阻塞", fontWeight = FontWeight.SemiBold)
                    simulationReport.blockers.forEach { blocker ->
                        Text(
                            "• $blocker",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (simulationReport.recommendedPath.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("推荐推进顺序", fontWeight = FontWeight.SemiBold)
                    simulationReport.recommendedPath.forEachIndexed { index, step ->
                        Text(
                            "${index + 1}. $step",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            SectionCard("外部 Reader 兼容性") {
                val trace = state.hceTrace
                DetailRow("已收到 APDU 帧", trace.frameCount.toString())
                DetailRow(
                    "链路状态",
                    when {
                        trace.frameCount == 0 && state.operatingMode == NfcOperatingMode.HCE -> "等待 Reader SELECT / APDU"
                        trace.frameCount == 0 -> "尚无 HCE 交互"
                        trace.deactivatedAtMs != null -> "已发生交互，链路已结束"
                        else -> "已发生 HCE 交互"
                    },
                )
                trace.deactivationReason?.let { reason ->
                    DetailRow(
                        "结束原因",
                        when (reason) {
                            0 -> "Reader 移出 / Link loss"
                            1 -> "服务被取消选择"
                            else -> "系统原因 $reason"
                        },
                    )
                }
                Text(
                    "这里仅记录帧计数和链路状态，不保存外部门禁 APDU 内容。若计数始终为 0，问题发生在 HostApduService 之前。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            InsightCard(
                title = "模拟卡分两类链路",
                message = "本页提供 Android HostApduService 的 ISO-DEP 实验模拟。小米钱包里的 M1 门卡则是 eSE/off-host 路由；这两类模拟能力由不同底层实现，不能互相替代。",
            )
        }
    }
}

private fun simulationSupportLabel(value: SimulationSupport): String = when (value) {
    SimulationSupport.SUPPORTED -> "可直接验证"
    SimulationSupport.PARTIAL -> "部分可控 / 继续逆向"
    SimulationSupport.REQUIRES_PROVISIONING -> "需要合法 Provisioning"
    SimulationSupport.UNSUPPORTED -> "当前路径不可控"
    SimulationSupport.UNKNOWN -> "待验证"
}

