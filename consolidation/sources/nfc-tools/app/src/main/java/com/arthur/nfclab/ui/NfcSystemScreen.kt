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
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.arthur.nfclab.domain.NfcCapability
import com.arthur.nfclab.domain.NfcDeviceProfile
import com.arthur.nfclab.domain.AccessReaderOutcome
import com.arthur.nfclab.domain.ProvisioningRequirementState
import com.arthur.nfclab.domain.ProvisioningRoute

@Composable
internal fun SystemScreen(
    contentPadding: PaddingValues,
    state: NfcToolsUiState,
    onRefreshDeviceProfile: () -> Unit,
    onRunRootDiagnostics: () -> Unit,
    onStartAccessDiagnostic: () -> Unit,
    onFinishAccessDiagnostic: (AccessReaderOutcome) -> Unit,
    onOpenWallet: (String) -> Unit,
    onClearAccessDiagnosticHistory: () -> Unit,
) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { SectionTitle("能力概览", "基于当前设备实际 NFC 栈动态识别") }
                IconButton(onClick = onRefreshDeviceProfile, enabled = !state.deviceProfileLoading) {
                    if (state.deviceProfileLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                }
            }
            Spacer(Modifier.height(10.dp))
            CapabilityGrid(state)
        }

        item {
            val profile = state.deviceProfile
            val vendor = profile?.vendor
            SectionCard(vendor?.displayName ?: "Android NFC 底层") {
                DetailRow("设备", profile?.identity?.let { "${it.manufacturer} ${it.model} (${it.device})" } ?: "等待读取")
                DetailRow("系统", profile?.identity?.osLabel ?: profile?.identity?.let { "Android ${it.androidRelease}" } ?: "未知")
                vendor?.firmware?.let { DetailRow("NFC 固件", it, mono = true) }
                vendor?.chipId?.let { DetailRow("NFC Chip ID", it, mono = true) }
                vendor?.port?.let { DetailRow("接口", it) }
                if (!profile?.wallets.isNullOrEmpty()) {
                    DetailRow(
                        "系统钱包",
                        profile.wallets.joinToString(" / ") { wallet ->
                            listOfNotNull(wallet.label, wallet.version).joinToString(" · ")
                        },
                    )
                }
                profile?.primaryEse?.let { ese ->
                    DetailRow("eSE", if (ese.connected == true) "已连接" else if (ese.available) "可用" else "不可用")
                }
                if (profile?.has(NfcCapability.UICC) == true) DetailRow("UICC NFC", "已支持")
                if (profile?.has(NfcCapability.MIFARE_READER) == true) DetailRow("MIFARE reader", "已支持")
                if (profile?.has(NfcCapability.MIFARE_OFF_HOST) == true) DetailRow("MIFARE off-host", "已支持")
                if (profile?.has(NfcCapability.TYPE4_NDEF_EMULATION) == true) DetailRow("Type-4 NDEF 模拟", "已支持")
                vendor?.extras?.get("mifareRoute")?.let { DetailRow("MIFARE CE route", it, mono = true) }
                vendor?.extras?.get("hostListenTechMask")?.let { DetailRow("Host listen mask", it, mono = true) }
                if (profile?.has(NfcCapability.VENDOR_NFC_API) == true) DetailRow("厂商 NFC API", "可用")
                vendor?.extras?.get("miNfcService")?.let { DetailRow("mi_nfc Binder", if (it == "true") "可用" else "未识别") }
                vendor?.extras?.get("nxpVendorService")?.let { DetailRow("NXP vendor Binder", if (it == "true") "可用" else "未识别") }
                vendor?.apiVersion?.let { DetailRow("Vendor API", "v$it") }
                vendor?.seRouting?.let { DetailRow("当前 SE route", routeLabel(it)) }
                vendor?.listenTechMask?.let { DetailRow("当前 Listen tech", techMaskLabel(it)) }
                vendor?.pollingTechMask?.let { DetailRow("当前 Polling tech", techMaskLabel(it)) }
            }
        }

        item {
            ProvisioningSummaryCard(
                state = state,
                onOpenWallet = onOpenWallet,
            )
        }

        item {
            AccessDiagnosticCard(
                state = state,
                onStart = onStartAccessDiagnostic,
                onFinish = onFinishAccessDiagnostic,
                onOpenWallet = onOpenWallet,
                onClearHistory = onClearAccessDiagnosticHistory,
            )
        }

        item {
            RootDiagnosticsCard(
                report = state.rootReport,
                running = state.rootRunning,
                onRun = onRunRootDiagnostics,
            )
        }

        item {
            SectionCard("安全边界") {
                Text(
                    "Root 功能聚焦于设备能力识别、路由观测和自有测试协议。工具不会自动恢复门禁密钥、导出支付凭证或修改 eSE 中的真实凭证。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProvisioningSummaryCard(
    state: NfcToolsUiState,
    onOpenWallet: (String) -> Unit,
) {
    SectionCard("安全卡 Provisioning") {
        val report = state.provisioningCapability
        if (report == null) {
            Text(
                "等待设备能力分析。Provisioning 会区分官方钱包、合作方 TSM 与 Direct eSE。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        report.routes.forEach { route ->
            DetailRow(route.title, provisioningReadinessLabel(route.readiness))
            route.requirements.firstOrNull { requirement ->
                requirement.state != ProvisioningRequirementState.SATISFIED &&
                    requirement.state != ProvisioningRequirementState.ACTION_AVAILABLE
            }?.let { missing ->
                DetailRow(
                    "下一条件",
                    "${missing.title} · ${provisioningRequirementStateLabel(missing.state)}",
                )
            }
            if (route.route == ProvisioningRoute.OEM_WALLET && route.providerId != null) {
                OutlinedButton(onClick = { onOpenWallet(route.providerId) }) {
                    Text("打开官方卡片管理")
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun CapabilityGrid(state: NfcToolsUiState) {
    val profile = state.deviceProfile
    val items = buildList {
        add(CapabilityPresentation(Icons.Outlined.Nfc, "NFC", state.nfcAvailable && state.nfcEnabled, "系统读写"))
        add(CapabilityPresentation(Icons.Outlined.Science, "HCE", state.supportsHce, "ISO-DEP"))
        add(CapabilityPresentation(Icons.Outlined.Security, "Root", profile?.rootAvailable == true, "su"))
        add(
            CapabilityPresentation(
                Icons.Outlined.Memory,
                "eSE",
                profile?.primaryEse?.let { it.connected ?: it.available } == true,
                "安全元件",
            ),
        )
        if (state.supportsHceF || profile?.has(NfcCapability.HCE_NFC_F) == true) {
            add(CapabilityPresentation(Icons.Outlined.Sensors, "HCE-F", true, "NFC-F"))
        }
        if (profile?.has(NfcCapability.UICC) == true) {
            add(CapabilityPresentation(Icons.Outlined.Memory, "UICC", true, "SIM 安全元件"))
        }
        if (profile?.has(NfcCapability.MIFARE_READER) == true) {
            add(CapabilityPresentation(Icons.Outlined.Sensors, "MIFARE", true, "Reader"))
        }
        if (profile?.has(NfcCapability.MIFARE_OFF_HOST) == true) {
            add(
                CapabilityPresentation(
                    Icons.Outlined.CreditCard,
                    "M1 off-host",
                    true,
                    profile.primaryWallet?.label ?: "安全元件",
                ),
            )
        }
        if (profile?.has(NfcCapability.TYPE4_NDEF_EMULATION) == true) {
            add(CapabilityPresentation(Icons.Outlined.CreditCard, "Type-4 NDEF", true, "厂商卡模拟"))
        }
        if (profile?.has(NfcCapability.VENDOR_NFC_API) == true) {
            add(
                CapabilityPresentation(
                    Icons.Outlined.Tune,
                    "Vendor API",
                    true,
                    profile.vendor?.displayName ?: "厂商扩展",
                ),
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { item ->
                    CapabilityCard(
                        modifier = Modifier.weight(1f),
                        icon = item.icon,
                        title = item.title,
                        available = item.available,
                        subtitle = item.subtitle,
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private data class CapabilityPresentation(
    val icon: ImageVector,
    val title: String,
    val available: Boolean,
    val subtitle: String,
)

@Composable
private fun RootDiagnosticsCard(report: String, running: Boolean, onRun: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    SectionCard("Root 深度诊断") {
        Text(
            "读取 NFC Framework、SELinux、HAL、Secure Element、路由与厂商扩展状态。输出中的凭证标识会自动脱敏。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onRun, enabled = !running) {
                if (running) {
                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (running) "诊断中" else "运行诊断")
            }
            OutlinedButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起详情" else "查看原始详情") }
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                SelectionContainer {
                    Text(
                        report,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
