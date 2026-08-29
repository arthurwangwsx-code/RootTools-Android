package com.arthur.nfclab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arthur.nfclab.domain.NfcCapability
import com.arthur.nfclab.domain.NfcCard
import com.arthur.nfclab.domain.NfcDeviceProfile
import com.arthur.nfclab.domain.NfcWalletInfo

@Composable
internal fun HomeScreen(
    contentPadding: PaddingValues,
    state: NfcToolsUiState,
    onNavigate: (AppTab) -> Unit,
    onRefreshDeviceProfile: () -> Unit,
    onOpenWallet: (providerId: String) -> Unit,
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
            DeviceHeroCard(
                nfcAvailable = state.nfcAvailable,
                nfcEnabled = state.nfcEnabled,
                supportsHce = state.supportsHce,
                profile = state.deviceProfile,
                loading = state.deviceProfileLoading,
                onRefresh = onRefreshDeviceProfile,
            )
        }

        item {
            SectionTitle("常用功能", "把高频操作放到第一屏")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction(
                    modifier = Modifier.weight(1f),
                    title = "读一张卡",
                    subtitle = "UID / 卡型 / 协议",
                    icon = Icons.Outlined.Nfc,
                    onClick = { onNavigate(AppTab.READER) },
                )
                QuickAction(
                    modifier = Modifier.weight(1f),
                    title = "HCE 测试",
                    subtitle = "ISO-DEP / APDU",
                    icon = Icons.Outlined.Science,
                    onClick = { onNavigate(AppTab.LAB) },
                )
            }
            Spacer(Modifier.height(12.dp))
            QuickActionWide(
                title = "查看系统 NFC 能力",
                subtitle = "Root · eSE · 路由 · 厂商扩展",
                icon = Icons.Outlined.Tune,
                onClick = { onNavigate(AppTab.SYSTEM) },
            )
        }

        item {
            WalletCardsSection(
                profile = state.deviceProfile,
                loading = state.deviceProfileLoading,
                onRefresh = onRefreshDeviceProfile,
                onOpenWallet = onOpenWallet,
            )
        }

        state.lastSnapshot?.let { snapshot ->
            item {
                SectionTitle("最近识别", "上一次读卡结果")
                Spacer(Modifier.height(10.dp))
                LastScanCard(snapshot = snapshot, onClick = { onNavigate(AppTab.READER) })
            }
        }

        if (state.deviceProfile?.has(NfcCapability.MIFARE_OFF_HOST) == true) {
            item {
                InsightCard(
                    title = "已识别到厂商 M1 off-host 模拟链路",
                    message = "当前设备可通过 Secure Element 处理 MIFARE Card Emulation；这与 Android HostApduService HCE 是不同链路。",
                )
            }
        }
    }
}

@Composable
private fun DeviceHeroCard(
    nfcAvailable: Boolean,
    nfcEnabled: Boolean,
    supportsHce: Boolean,
    profile: NfcDeviceProfile?,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                ) {
                    Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        profile?.identity?.model ?: "正在识别设备",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        profile?.identity?.let { "${it.device} · Android ${it.androidRelease}" }
                            ?: "读取 NFC 与 Root 能力",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                    )
                }
                IconButton(onClick = onRefresh, enabled = !loading) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("NFC", nfcAvailable && nfcEnabled)
                StatusPill("Root", profile?.rootAvailable == true)
                StatusPill("eSE", profile?.primaryEse?.let { it.connected ?: it.available } == true)
                StatusPill("HCE", supportsHce)
            }

            profile?.let {
                Text(
                    buildString {
                        append(it.identity.osLabel ?: it.identity.manufacturer)
                        it.vendor?.firmware?.let { fw -> append(" · NFC FW ").append(fw) }
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun QuickAction(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ElevatedCard(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(9.dp).size(22.dp))
            }
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuickActionWide(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WalletCardsSection(
    profile: NfcDeviceProfile?,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpenWallet: (providerId: String) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle(
                    when (profile?.wallets?.size) {
                        1 -> "${profile.primaryWallet?.label}卡片"
                        in 2..Int.MAX_VALUE -> "系统钱包卡片"
                        else -> "设备卡片"
                    },
                    "只展示已授权读取的卡片元数据，不读取支付凭证",
                )
            }
            IconButton(onClick = onRefresh, enabled = !loading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "刷新卡片")
            }
        }
        Spacer(Modifier.height(10.dp))

        when {
            loading && profile == null -> LoadingCard("正在读取设备 NFC 能力与卡片…")
            profile == null -> EmptyStateCard("尚未读取设备 NFC 状态", "点击刷新后读取 NFC、Root、安全元件与厂商扩展。")
            profile.wallets.isEmpty() && profile.cards.isEmpty() -> EmptyStateCard(
                title = "当前设备没有可用的厂商卡片源",
                subtitle = "读卡与标准 HCE 仍可正常使用；后续可以通过新的厂商 Provider 扩展卡片能力。",
            )
            profile.cards.isEmpty() -> EmptyStateCard(
                title = "没有读取到设备卡片",
                subtitle = profile.error ?: "可以先在系统钱包中添加卡片，然后返回刷新。",
            )
            else -> {
                LazyRow(
                    contentPadding = PaddingValues(end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(profile.cards, key = { it.id }) { card -> DeviceWalletCard(card) }
                }
                if (profile.wallets.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        if (profile != null && profile.wallets.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            WalletManagementActions(profile.wallets, onOpenWallet)
        }
    }
}

@Composable
private fun WalletManagementActions(
    wallets: List<NfcWalletInfo>,
    onOpenWallet: (providerId: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        wallets.forEach { wallet ->
            OutlinedButton(
                onClick = { onOpenWallet(wallet.providerId) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("在${wallet.label}中管理")
            }
        }
    }
}

@Composable
private fun DeviceWalletCard(card: NfcCard) {
    Card(
        modifier = Modifier.width(280.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (card.active) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = if (card.active) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
                    Icon(Icons.Outlined.CreditCard, contentDescription = null, modifier = Modifier.padding(9.dp).size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        card.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(card.technologyLabel, style = MaterialTheme.typography.labelMedium)
                }
            }

            Text(
                "${card.sourceLabel} · ${routeLabel(card.route)} · ${card.shortId}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
                    Text(
                        if (card.active) "当前使用" else "已添加",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (card.metadata["sectorOverwritten"] == "true") {
                    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
                        Text("扇区已写入", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
