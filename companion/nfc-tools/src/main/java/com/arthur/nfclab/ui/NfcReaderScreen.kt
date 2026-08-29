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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arthur.nfclab.domain.NfcOperatingMode
import com.arthur.nfclab.nfc.TagSnapshot
import com.arthur.nfclab.nfc.TagCompatibilityComparator
import com.arthur.nfclab.nfc.TagCompatibilityComparison

@Composable
internal fun ReaderScreen(
    contentPadding: PaddingValues,
    state: NfcToolsUiState,
    onModeChange: (NfcOperatingMode) -> Unit,
    onClearHistory: () -> Unit,
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
            ReaderHero(
                enabled = state.nfcEnabled && state.operatingMode == NfcOperatingMode.READER,
                onEnable = { onModeChange(NfcOperatingMode.READER) },
            )
        }

        item {
            if (state.lastSnapshot == null) {
                EmptyStateCard(
                    title = "等待 NFC 卡片",
                    subtitle = "把卡贴近手机 NFC 天线区域。识别后会自动显示 UID、卡型、ATQA / SAK、NDEF 与协议能力。",
                )
            } else {
                SectionTitle("本次识别", "公开协议与标签元数据")
                Spacer(Modifier.height(10.dp))
                SnapshotCard(state.lastSnapshot)
            }
        }

        TagCompatibilityComparator.latestDistinctPair(state.history)?.let { (left, right) ->
            item {
                TagComparisonCard(TagCompatibilityComparator.compare(left, right))
            }
        }

        if (state.history.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { SectionTitle("最近记录", "保存在应用私有目录") }
                    OutlinedButton(onClick = onClearHistory) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("清空")
                    }
                }
            }
            items(state.history.take(12), key = { it.timestampMs }) { snapshot -> HistoryItem(snapshot) }
        }
    }
}

@Composable
private fun TagComparisonCard(comparison: TagCompatibilityComparison) {
    SectionCard("最近两张实体卡对比") {
        Text(
            "仅比较公开 RF / ISO-DEP / NXP 产品指纹，不读取密钥或受保护数据。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        DetailRow("卡 A", comparison.left.idHex, mono = true)
        DetailRow("卡 B", comparison.right.idHex, mono = true)
        DetailRow(
            "Tech 栈",
            if (comparison.sameTechnologySet) "一致" else "不同",
        )
        comparison.sameProduct?.let { same ->
            DetailRow("NXP 产品", if (same) "一致" else "不同")
        }
        if (comparison.differences.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            comparison.differences.take(8).forEach { diff ->
                Text(
                    diff.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "A: ${diff.left}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "B: ${diff.right}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            comparison.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReaderHero(enabled: Boolean, onEnable: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)) {
                Icon(Icons.Outlined.Sensors, contentDescription = null, modifier = Modifier.padding(18.dp).size(40.dp))
            }
            Text(
                if (enabled) "读卡器已就绪" else "读卡器未启用",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (enabled) "保持此页面，把卡贴近手机 NFC 天线区域。" else "点击下方按钮即可进入 Reader Mode。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.74f),
            )
            if (!enabled) FilledTonalButton(onClick = onEnable) { Text("进入读卡模式") }
        }
    }
}

@Composable
private fun SnapshotCard(snapshot: TagSnapshot) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Nfc, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(snapshot.idHex, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text(
                        snapshot.technologies.joinToString(" · ") { it.substringAfterLast('.') },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
            SnapshotBody(snapshot)
        }
    }
}
