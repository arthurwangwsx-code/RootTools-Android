package com.arthur.roottools.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class ToolId {
    DASHBOARD,
    PERFORMANCE,
    ROOT_ADB,
    PERMISSIONS,
    STARTUP,
    APPS,
    DIAGNOSTICS,
    MODULES,
    ACTIONS,
    NETWORK,
    STORAGE,
    BATTERY,
    SHIZUKU,
    COMPONENTS,
}

enum class ToolCategory {
    DAILY,
    GOVERNANCE,
    DIAGNOSTICS,
    SYSTEM,
}

enum class ToolCapability {
    ROOT,
    NOTIFICATION,
    MAGISK,
    VECTOR,
    NETWORK,
    SHIZUKU,
    FRAMEWORK_PRIVILEGE,
}

data class ToolDefinition(
    val id: ToolId,
    val category: ToolCategory,
    val title: String,
    val icon: ImageVector,
    val accent: Color,
    val requiredCapabilities: Set<ToolCapability> = emptySet(),
    val implemented: Boolean = true,
)

object ToolRegistry {
    val tools: List<ToolDefinition> = listOf(
        ToolDefinition(ToolId.DASHBOARD, ToolCategory.DAILY, "设备看板", Icons.Rounded.Dashboard, Color(0xFF8EE3FF), setOf(ToolCapability.ROOT)),
        ToolDefinition(ToolId.PERFORMANCE, ToolCategory.DAILY, "性能控制", Icons.Rounded.Speed, Color(0xFFA9F5D0), setOf(ToolCapability.ROOT, ToolCapability.NOTIFICATION)),
        ToolDefinition(ToolId.ROOT_ADB, ToolCategory.DAILY, "Root ADB", Icons.Rounded.WifiTethering, Color(0xFFB9C8FF), setOf(ToolCapability.ROOT, ToolCapability.NETWORK)),
        ToolDefinition(ToolId.PERMISSIONS, ToolCategory.DAILY, "权限中心", Icons.Rounded.VerifiedUser, Color(0xFFFFC56F)),
        ToolDefinition(ToolId.STARTUP, ToolCategory.GOVERNANCE, "启动治理", Icons.Rounded.Tune, Color(0xFF9BD4FF), setOf(ToolCapability.ROOT)),
        ToolDefinition(ToolId.APPS, ToolCategory.GOVERNANCE, "应用治理", Icons.Rounded.Security, Color(0xFFB7B7FF), setOf(ToolCapability.FRAMEWORK_PRIVILEGE)),
        ToolDefinition(ToolId.DIAGNOSTICS, ToolCategory.DIAGNOSTICS, "进程诊断", Icons.Rounded.Terminal, Color(0xFFFFB58E), setOf(ToolCapability.ROOT)),
        ToolDefinition(ToolId.MODULES, ToolCategory.SYSTEM, "Root 模块", Icons.Rounded.Memory, Color(0xFFD5E49C), setOf(ToolCapability.ROOT, ToolCapability.MAGISK, ToolCapability.VECTOR)),
        ToolDefinition(ToolId.ACTIONS, ToolCategory.SYSTEM, "常用操作", Icons.Rounded.Bolt, Color(0xFFFFD28A), setOf(ToolCapability.ROOT)),
        ToolDefinition(ToolId.NETWORK, ToolCategory.DIAGNOSTICS, "网络诊断", Icons.Rounded.SettingsEthernet, Color(0xFF9ED8C8), setOf(ToolCapability.ROOT, ToolCapability.NETWORK)),
        ToolDefinition(ToolId.STORAGE, ToolCategory.DIAGNOSTICS, "存储与 IO", Icons.Rounded.Memory, Color(0xFFB7D7A8), setOf(ToolCapability.ROOT)),
        ToolDefinition(ToolId.BATTERY, ToolCategory.DAILY, "电池与温控", Icons.Rounded.Thermostat, Color(0xFFFFC98B), setOf(ToolCapability.ROOT)),
        ToolDefinition(ToolId.SHIZUKU, ToolCategory.SYSTEM, "Shizuku / Sui", Icons.Rounded.Share, Color(0xFF8FD8FF), setOf(ToolCapability.SHIZUKU)),
        ToolDefinition(ToolId.COMPONENTS, ToolCategory.GOVERNANCE, "组件管理", Icons.Rounded.Tune, Color(0xFFB5D5FF), setOf(ToolCapability.FRAMEWORK_PRIVILEGE)),
    )
}
