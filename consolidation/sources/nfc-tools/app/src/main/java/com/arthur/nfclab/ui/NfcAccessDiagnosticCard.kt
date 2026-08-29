package com.arthur.nfclab.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arthur.nfclab.domain.AccessDiagnosticConclusion
import com.arthur.nfclab.domain.AccessDiagnosticReport
import com.arthur.nfclab.domain.AccessReaderOutcome
import java.text.DateFormat
import java.util.Date

@Composable
internal fun AccessDiagnosticCard(
    state: NfcToolsUiState,
    onStart: () -> Unit,
    onFinish: (AccessReaderOutcome) -> Unit,
    onOpenWallet: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    val diagnostic = state.accessDiagnostic
    val profile = state.deviceProfile
    val activeCard = profile?.activeCard
    val wallet = profile?.primaryWallet

    SectionCard("读卡器兼容性诊断") {
        Text(
            "用于你有权测试的卡片与读卡器。只记录 RF Field、协议层和 eSE/NFCEE 状态摘要。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        if (!diagnostic.supported) {
            EmptyStateCard(
                title = "当前设备暂无深度诊断适配",
                subtitle = "普通 Reader/HCE 仍可使用；深度 RF/eSE 诊断目前已适配 Xiaomi Reader Detector。",
            )
            return@SectionCard
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.CreditCard, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text("当前激活卡片", style = MaterialTheme.typography.labelMedium)
                    Text(
                        activeCard?.title ?: "尚未检测到激活卡",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    activeCard?.let {
                        Text(
                            "${it.sourceLabel} · ${it.technologyLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (wallet != null && diagnostic.phase !in setOf(AccessDiagnosticPhase.RUNNING, AccessDiagnosticPhase.ANALYZING)) {
                    OutlinedButton(onClick = { onOpenWallet(wallet.providerId) }) { Text("切换") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        when (diagnostic.phase) {
            AccessDiagnosticPhase.IDLE -> {
                Text(
                    "开始后保持 NFC Tools 在前台，把手机按正常使用姿势靠近目标读卡器 2–3 秒，然后选择现场结果。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onStart, enabled = activeCard != null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Nfc, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(7.dp))
                    Text("开始记录交互")
                }
            }

            AccessDiagnosticPhase.STARTING -> DiagnosticProgress("正在启动 RF/eSE 观测…")

            AccessDiagnosticPhase.RUNNING -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Icon(Icons.Outlined.RadioButtonChecked, contentDescription = null)
                            Text("正在记录", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        Text("现在靠近读卡器 2–3 秒；观察结果后先把手机移开，再按下面的实际现场表现结束本次诊断。")
                    }
                }
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = { onFinish(AccessReaderOutcome.OPENED) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("识别成功") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onFinish(AccessReaderOutcome.REACTED_BUT_FAILED) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("设备有提示，但未通过") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onFinish(AccessReaderOutcome.NO_REACTION) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("设备完全无提示") }
            }

            AccessDiagnosticPhase.ANALYZING -> DiagnosticProgress("正在等待 RF Field 结束并分析证据…")

            AccessDiagnosticPhase.COMPLETE -> {
                diagnostic.report?.let { report ->
                    AccessDiagnosticReportView(report = report, history = diagnostic.history)
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = onStart, enabled = activeCard != null, modifier = Modifier.fillMaxWidth()) {
                    Text("再测一次")
                }
            }

            AccessDiagnosticPhase.ERROR -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(diagnostic.error ?: "诊断失败", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = onStart, enabled = activeCard != null, modifier = Modifier.fillMaxWidth()) {
                    Text("重新开始")
                }
            }
        }

        if (diagnostic.history.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("最近诊断", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = onClearHistory) { Text("清空") }
            }
            diagnostic.history.take(4).forEach { report ->
                DiagnosticHistoryRow(report)
            }
        }
    }
}

@Composable
private fun DiagnosticProgress(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(message)
    }
}

@Composable
private fun AccessDiagnosticReportView(report: AccessDiagnosticReport, history: List<AccessDiagnosticReport>) {
    val successfulBaseline = history.firstOrNull {
        it.sessionId != report.sessionId &&
            it.outcome == AccessReaderOutcome.OPENED &&
            it.cardTitle != report.cardTitle
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = when (report.conclusion) {
            AccessDiagnosticConclusion.SUCCESS -> MaterialTheme.colorScheme.secondaryContainer
            AccessDiagnosticConclusion.NO_RF_FIELD -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.tertiaryContainer
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(
                    if (report.conclusion == AccessDiagnosticConclusion.SUCCESS) Icons.Outlined.CheckCircle else Icons.Outlined.Nfc,
                    contentDescription = null,
                )
                Text(conclusionLabel(report.conclusion), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(report.summary)
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("技术证据", fontWeight = FontWeight.SemiBold)
    report.evidence.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    successfulBaseline?.let {
        Spacer(Modifier.height(12.dp))
        InsightCard("与最近成功基线对比", compareWithBaseline(it, report))
    }
    Spacer(Modifier.height(12.dp))
    Text("下一步", fontWeight = FontWeight.SemiBold)
    report.recommendations.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun DiagnosticHistoryRow(report: AccessDiagnosticReport) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(report.cardTitle ?: "未命名卡片", fontWeight = FontWeight.Medium)
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(report.finishedAtMs)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(outcomeLabel(report.outcome), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun conclusionLabel(value: AccessDiagnosticConclusion): String = when (value) {
    AccessDiagnosticConclusion.SUCCESS -> "链路正常"
    AccessDiagnosticConclusion.NO_RF_FIELD -> "未检测到 13.56 MHz RF Field"
    AccessDiagnosticConclusion.RF_FIELD_NO_CARD_INTERACTION -> "有 RF 场，但未进入卡片交互"
    AccessDiagnosticConclusion.CARD_INTERACTION_AUTH_FAILED -> "已进入卡片交互，后续校验未通过"
    AccessDiagnosticConclusion.CARD_INTERACTION_NO_READER_FEEDBACK -> "底层已交互，设备无明显反馈"
    AccessDiagnosticConclusion.INCONCLUSIVE -> "证据不足，需要再测一次"
}

private fun outcomeLabel(value: AccessReaderOutcome): String = when (value) {
    AccessReaderOutcome.OPENED -> "识别成功"
    AccessReaderOutcome.REACTED_BUT_FAILED -> "有提示未通过"
    AccessReaderOutcome.NO_REACTION -> "无提示"
}

private fun compareWithBaseline(baseline: AccessDiagnosticReport, current: AccessDiagnosticReport): String {
    val base = baseline.signals
    val now = current.signals
    return when {
        base.rfFieldSeen && !now.rfFieldSeen ->
            "成功基线能检测到 13.56 MHz RF Field，而本次没有。若使用姿势一致，应优先检查目标读卡器的工作频段或发场方式。"
        base.cardInteractionSeen && now.rfFieldSeen && !now.cardInteractionSeen ->
            "两边都有 13.56 MHz RF Field，但成功基线能进入卡片/eSE 交互，本次没有，差异更集中在协议兼容阶段。"
        base.cardInteractionSeen && now.cardInteractionSeen ->
            "成功基线和本次都已进入卡片/eSE 交互，因此手机硬件与 13.56 MHz 链路不是主要差异，应继续比较后续校验流程。"
        else -> "两次记录的底层证据还不足以形成唯一差异，建议保持相同姿势各重复一次。"
    }
}
