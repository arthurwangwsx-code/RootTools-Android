package com.arthur.roottools.app.navigation

import androidx.annotation.StringRes
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
import com.arthur.roottools.R

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
    PERMISSION_OPS,
    INTEGRITY,
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
    PACKAGE_CONTROL,
}

data class ToolDefinition(
    val id: ToolId,
    val category: ToolCategory,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val accent: Color,
    val requiredCapabilities: Set<ToolCapability> = emptySet(),
    val implemented: Boolean = true,
)

object ToolRegistry {
    val tools: List<ToolDefinition> = listOf(
        ToolDefinition(ToolId.DASHBOARD, ToolCategory.DAILY, R.string.tool_dashboard_title, Icons.Rounded.Dashboard, Color(0xFF8EE3FF), setOf(ToolCapability.ROOT)),
        ToolDefinition(ToolId.PERFORMANCE, ToolCategory.DAILY, R.string.tool_performance_title, Icons.Rounded.Speed, Color(0xFFA9F5D0), setOf(ToolCapability.ROOT, ToolCapability.NOTIFICATION)),
        ToolDefinition(ToolId.ROOT_ADB, ToolCategory.DAILY, R.string.tool_adb_title, Icons.Rounded.WifiTethering, Color(0xFFB9C8FF), setOf(ToolCapability.ROOT, ToolCapability.NETWORK)),
        ToolDefinition(ToolId.PERMISSIONS, ToolCategory.DAILY, R.string.tool_permissions_title, Icons.Rounded.VerifiedUser, Color(0xFFFFC56F)),
        ToolDefinition(ToolId.STARTUP, ToolCategory.GOVERNANCE, R.string.tool_startup_title, Icons.Rounded.Tune, Color(0xFF9BD4FF), setOf(ToolCapability.ROOT)),
        ToolDefinition(ToolId.APPS, ToolCategory.GOVERNANCE, R.string.tool_apps_title, Icons.Rounded.Security, Color(0xFFB7B7FF), setOf(ToolCapability.PACKAGE_CONTROL)),
        ToolDefinition(ToolId.DIAGNOSTICS, ToolCategory.DIAGNOSTICS, R.string.tool_diagnostics_title, Icons.Rounded.Terminal, Color(0xFFFFB58E), setOf(ToolCapability.ROOT)),
        ToolDefinition(ToolId.INTEGRITY, ToolCategory.DIAGNOSTICS, R.string.tool_integrity_title, Icons.Rounded.VerifiedUser, Color(0xFF9ED9C5)),
        ToolDefinition(ToolId.MODULES, ToolCategory.SYSTEM, R.string.tool_modules_title, Icons.Rounded.Memory, Color(0xFFD5E49C), setOf(ToolCapability.ROOT, ToolCapability.MAGISK, ToolCapability.VECTOR)),
        ToolDefinition(ToolId.ACTIONS, ToolCategory.SYSTEM, R.string.tool_actions_title, Icons.Rounded.Bolt, Color(0xFFFFD28A), setOf(ToolCapability.ROOT)),
        ToolDefinition(ToolId.NETWORK, ToolCategory.DIAGNOSTICS, R.string.tool_network_title, Icons.Rounded.SettingsEthernet, Color(0xFF9ED8C8), setOf(ToolCapability.ROOT, ToolCapability.NETWORK)),
        ToolDefinition(ToolId.STORAGE, ToolCategory.DIAGNOSTICS, R.string.tool_storage_title, Icons.Rounded.Memory, Color(0xFFB7D7A8), setOf(ToolCapability.ROOT)),
        ToolDefinition(ToolId.BATTERY, ToolCategory.DAILY, R.string.tool_battery_title, Icons.Rounded.Thermostat, Color(0xFFFFC98B), setOf(ToolCapability.ROOT)),
        ToolDefinition(ToolId.SHIZUKU, ToolCategory.SYSTEM, R.string.tool_shizuku_title, Icons.Rounded.Share, Color(0xFF8FD8FF), setOf(ToolCapability.SHIZUKU)),
    )
}
