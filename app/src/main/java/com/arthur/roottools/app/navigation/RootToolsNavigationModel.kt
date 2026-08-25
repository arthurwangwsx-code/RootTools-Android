package com.arthur.roottools.app.navigation

enum class RootToolsTab(
    val graphRoute: String,
    val landingRoute: String,
) {
    HOME("graph/home", "home"),
    APPS("graph/apps", "apps"),
    DEVICE("graph/device", "device"),
    DIAGNOSTICS("graph/diagnostics", "diagnostics"),
    SYSTEM("graph/system", "system"),
}
enum class RootToolsDestination(
    val route: String,
    val tab: RootToolsTab,
    val toolId: ToolId? = null,
) {
    HOME("home", RootToolsTab.HOME),
    APPS("apps", RootToolsTab.APPS),
    DEVICE("device", RootToolsTab.DEVICE),
    DIAGNOSTICS("diagnostics", RootToolsTab.DIAGNOSTICS),
    SYSTEM("system", RootToolsTab.SYSTEM),

    HEALTH_DASHBOARD("diagnostics/health", RootToolsTab.DIAGNOSTICS, ToolId.DASHBOARD),
    PERFORMANCE("device/performance", RootToolsTab.DEVICE, ToolId.PERFORMANCE),
    SHADOW_DISPLAY("device/shadow-display", RootToolsTab.DEVICE, ToolId.SHADOW_DISPLAY),
    AGENT_SESSION("device/agent-session", RootToolsTab.DEVICE, ToolId.AGENT_SESSION),
    ADB("device/adb", RootToolsTab.DEVICE, ToolId.ROOT_ADB),
    PERMISSIONS("apps/permissions", RootToolsTab.APPS, ToolId.PERMISSIONS),
    STARTUP("apps/startup", RootToolsTab.APPS, ToolId.STARTUP),
    APP_CONTROL("apps/control", RootToolsTab.APPS, ToolId.APPS),
    AD_GOVERNANCE("apps/ad-governance", RootToolsTab.APPS, ToolId.AD_GOVERNANCE),
    PROCESS_DIAGNOSTICS("diagnostics/process", RootToolsTab.DIAGNOSTICS, ToolId.DIAGNOSTICS),
    INTEGRITY("diagnostics/integrity", RootToolsTab.DIAGNOSTICS, ToolId.INTEGRITY),
    MODULES("system/modules", RootToolsTab.SYSTEM, ToolId.MODULES),
    ACTIONS("system/actions", RootToolsTab.SYSTEM, ToolId.ACTIONS),
    NETWORK("device/network", RootToolsTab.DEVICE, ToolId.NETWORK),
    STORAGE("device/storage", RootToolsTab.DEVICE, ToolId.STORAGE),
    BATTERY("device/battery", RootToolsTab.DEVICE, ToolId.BATTERY),
    SHIZUKU("system/shizuku", RootToolsTab.SYSTEM, ToolId.SHIZUKU),
    COMPONENTS("apps/components", RootToolsTab.APPS, ToolId.COMPONENTS),
    PERMISSION_OPS("apps/appops", RootToolsTab.APPS, ToolId.PERMISSION_OPS),
    DEVELOPER_RUNTIME("system/developer-runtime", RootToolsTab.SYSTEM, ToolId.DEVELOPER_RUNTIME),
}
object RootToolsNavigationPolicy {
    private val byToolId = RootToolsDestination.entries
        .mapNotNull { destination -> destination.toolId?.let { it to destination } }
        .toMap()

    fun destinationFor(toolId: ToolId): RootToolsDestination =
        requireNotNull(byToolId[toolId]) { "No destination registered for $toolId" }

    fun tabForRoute(route: String?): RootToolsTab = when {
        route == null -> RootToolsTab.HOME
        route == RootToolsTab.HOME.graphRoute || route == RootToolsTab.HOME.landingRoute -> RootToolsTab.HOME
        route == RootToolsTab.APPS.graphRoute || route.startsWith("apps") -> RootToolsTab.APPS
        route == RootToolsTab.DEVICE.graphRoute || route.startsWith("device") -> RootToolsTab.DEVICE
        route == RootToolsTab.DIAGNOSTICS.graphRoute || route.startsWith("diagnostics") -> RootToolsTab.DIAGNOSTICS
        route == RootToolsTab.SYSTEM.graphRoute || route.startsWith("system") -> RootToolsTab.SYSTEM
        else -> RootToolsTab.HOME
    }

    fun externalScreen(screen: String?): RootToolsDestination? = when (screen?.trim()?.lowercase()) {
        "adb" -> RootToolsDestination.ADB
        "integrity" -> RootToolsDestination.INTEGRITY
        "shadow", "shadow-display" -> RootToolsDestination.SHADOW_DISPLAY
        "agent", "agent-session" -> RootToolsDestination.AGENT_SESSION
        else -> null
    }
}
