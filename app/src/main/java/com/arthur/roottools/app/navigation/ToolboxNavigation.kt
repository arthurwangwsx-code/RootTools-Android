package com.arthur.roottools.app.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class ToolboxRoute {
    HOME,
    DASHBOARD,
    PERFORMANCE,
    ADB,
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

internal data class ToolboxCard(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val route: ToolboxRoute?,
    val badge: String? = null,
)

internal fun routeFor(id: ToolId): ToolboxRoute = when (id) {
    ToolId.DASHBOARD -> ToolboxRoute.DASHBOARD
    ToolId.PERFORMANCE -> ToolboxRoute.PERFORMANCE
    ToolId.ROOT_ADB -> ToolboxRoute.ADB
    ToolId.PERMISSIONS -> ToolboxRoute.PERMISSIONS
    ToolId.STARTUP -> ToolboxRoute.STARTUP
    ToolId.APPS -> ToolboxRoute.APPS
    ToolId.DIAGNOSTICS -> ToolboxRoute.DIAGNOSTICS
    ToolId.MODULES -> ToolboxRoute.MODULES
    ToolId.ACTIONS -> ToolboxRoute.ACTIONS
    ToolId.NETWORK -> ToolboxRoute.NETWORK
    ToolId.STORAGE -> ToolboxRoute.STORAGE
    ToolId.BATTERY -> ToolboxRoute.BATTERY
    ToolId.SHIZUKU -> ToolboxRoute.SHIZUKU
    ToolId.COMPONENTS -> ToolboxRoute.COMPONENTS
    ToolId.PERMISSION_OPS -> ToolboxRoute.PERMISSION_OPS
    ToolId.INTEGRITY -> ToolboxRoute.INTEGRITY
}
