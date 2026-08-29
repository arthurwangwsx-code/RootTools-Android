package com.arthur.nfclab.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight

internal enum class AppTab(
    val title: String,
    val icon: ImageVector,
) {
    HOME("首页", Icons.Outlined.Home),
    READER("读卡", Icons.Outlined.Nfc),
    LAB("模拟", Icons.Outlined.CreditCard),
    SYSTEM("系统", Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NfcToolsShell(
    selectedTab: AppTab,
    onNavigate: (AppTab) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when (selectedTab) {
                                AppTab.HOME -> "NFC Tools"
                                AppTab.READER -> "读卡与识别"
                                AppTab.LAB -> "模拟卡实验室"
                                AppTab.SYSTEM -> "设备与系统"
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            when (selectedTab) {
                                AppTab.HOME -> "设备 NFC 能力与卡片中心"
                                AppTab.READER -> "把卡贴近手机背面即可自动识别"
                                AppTab.LAB -> "ISO-DEP HCE 模拟与兼容性验证"
                                AppTab.SYSTEM -> "Root、安全元件与厂商扩展"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { onNavigate(tab) },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
        content = content,
    )
}
