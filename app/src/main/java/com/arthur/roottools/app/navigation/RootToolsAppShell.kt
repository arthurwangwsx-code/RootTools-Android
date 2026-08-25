package com.arthur.roottools.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.arthur.roottools.R
import com.arthur.roottools.app.rootToolsContainer
import com.arthur.roottools.core.agent.AgentSessionState
import com.arthur.roottools.app.adgovernance.AdGovernanceRoute
import com.arthur.roottools.app.agent.AgentSessionRoute
import com.arthur.roottools.app.home.DomainLandingScreen
import com.arthur.roottools.app.home.ProductHomeScreen
import com.arthur.roottools.app.shadow.ShadowDisplayRoute
import com.arthur.roottools.feature.dashboard.ui.HealthDashboardScreen
import com.arthur.roottools.feature.developer.DeveloperRuntimeRoute
import com.arthur.roottools.feature.home.presentation.HomeHealthInput
import com.arthur.roottools.feature.home.presentation.HomeHealthPolicy
import com.arthur.roottools.feature.integrity.ui.EnvironmentIntegrityRoute
import com.arthur.roottools.feature.performance.ui.PerformanceScreen
import com.arthur.roottools.ui.AdbScreen
import com.arthur.roottools.ui.AppControlCenterScreen
import com.arthur.roottools.ui.BatteryThermalScreen
import com.arthur.roottools.ui.CommonActionsScreen
import com.arthur.roottools.ui.ComponentManagerScreen
import com.arthur.roottools.ui.DashboardUiState
import com.arthur.roottools.ui.DashboardViewModel
import com.arthur.roottools.ui.DiagnosticsScreen
import com.arthur.roottools.ui.ModuleCenterScreen
import com.arthur.roottools.ui.NetworkDiagnosticsScreen
import com.arthur.roottools.ui.PermissionAppOpsScreen
import com.arthur.roottools.ui.PermissionScreen
import com.arthur.roottools.ui.ShizukuSuiScreen
import com.arthur.roottools.ui.StartupScreen
import com.arthur.roottools.ui.StorageDiagnosticsScreen
import com.arthur.roottools.ui.toHealthDashboardUiState
import com.arthur.roottools.ui.toPerformanceUiState

@Composable
fun RootToolsAppShell(
    viewModel: DashboardViewModel,
    initialScreen: String? = null,
    initialScreenRequestVersion: Long = 0L,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val agentSession by LocalContext.current.rootToolsContainer.agentSessionManager.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val currentTab = RootToolsTab.entries.firstOrNull { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.graphRoute } == true
    } ?: RootToolsNavigationPolicy.tabForRoute(currentRoute)
    val diagnosticBadge = diagnosticAttentionCount(state)

    LaunchedEffect(initialScreen, initialScreenRequestVersion) {
        RootToolsNavigationPolicy.externalScreen(initialScreen)?.let { destination ->
            navigateToDestination(navController, destination)
        }
    }

    LaunchedEffect(currentRoute) {
        viewModel.setDashboardSampling(
            currentRoute == RootToolsDestination.HEALTH_DASHBOARD.route ||
                currentRoute == RootToolsDestination.BATTERY.route,
        )
        when (currentRoute) {
            RootToolsDestination.PERFORMANCE.route -> viewModel.loadPerformanceExplain()
            RootToolsDestination.ADB.route -> viewModel.loadAdb()
            RootToolsDestination.PERMISSIONS.route -> viewModel.loadModules()
            RootToolsDestination.STARTUP.route -> viewModel.loadStartup()
            RootToolsDestination.APP_CONTROL.route -> viewModel.loadAppControl()
            RootToolsDestination.PROCESS_DIAGNOSTICS.route -> viewModel.loadDiagnostics()
            RootToolsDestination.MODULES.route -> viewModel.loadModules()
            RootToolsDestination.ACTIONS.route -> viewModel.loadAudit()
            RootToolsDestination.NETWORK.route -> viewModel.loadNetwork()
            RootToolsDestination.STORAGE.route -> viewModel.loadStorage()
            RootToolsDestination.SHIZUKU.route -> viewModel.refreshShizuku()
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            RootToolsTab.entries.forEach { tab ->
                item(
                    icon = {
                        if (tab == RootToolsTab.DIAGNOSTICS && diagnosticBadge > 0) {
                            BadgedBox(
                                badge = { Badge { Text(diagnosticBadge.toString()) } },
                            ) {
                                Icon(tab.icon(), contentDescription = stringResource(tab.labelRes()))
                            }
                        } else {
                            Icon(tab.icon(), contentDescription = stringResource(tab.labelRes()))
                        }
                    },
                    label = { Text(stringResource(tab.labelRes())) },
                    selected = currentTab == tab,
                    onClick = { navigateToTab(navController, tab) },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        RootToolsNavHost(
            navController = navController,
            state = state,
            agentSession = agentSession,
            viewModel = viewModel,
        )
    }
}
@Composable
private fun RootToolsNavHost(
    navController: androidx.navigation.NavHostController,
    state: DashboardUiState,
    agentSession: AgentSessionState,
    viewModel: DashboardViewModel,
) {
    val open: (RootToolsDestination) -> Unit = { navigateToDestination(navController, it) }
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = RootToolsTab.HOME.graphRoute,
        modifier = Modifier,
    ) {
        navigation(
            route = RootToolsTab.HOME.graphRoute,
            startDestination = RootToolsDestination.HOME.route,
        ) {
            composable(RootToolsDestination.HOME.route) {
                ProductHomeScreen(
                    state = state,
                    agentSession = agentSession,
                    onRefresh = viewModel::refresh,
                    onNavigate = open,
                )
            }
        }

        navigation(
            route = RootToolsTab.APPS.graphRoute,
            startDestination = RootToolsDestination.APPS.route,
        ) {
            composable(RootToolsDestination.APPS.route) {
                DomainLandingScreen(RootToolsTab.APPS, state, open)
            }
            composable(RootToolsDestination.PERMISSIONS.route) {
                PermissionScreen(
                    state = state,
                    onBack = back,
                    onRefresh = viewModel::refresh,
                    onRequestRoot = viewModel::requestRoot,
                    onRequestShizuku = viewModel::requestShizukuPermission,
                )
            }
            composable(RootToolsDestination.STARTUP.route) {
                StartupScreen(state, back, viewModel::loadStartup)
            }
            composable(RootToolsDestination.APP_CONTROL.route) {
                AppControlCenterScreen(
                    state = state,
                    onBack = back,
                    onRefresh = viewModel::loadAppControl,
                    onSelectApp = viewModel::loadAppControlDetail,
                    onCloseDetail = viewModel::clearAppControlDetail,
                    onFreeze = viewModel::freezePackage,
                    onEnable = viewModel::enablePackage,
                    onForceStop = viewModel::forceStopPackage,
                    onBucket = viewModel::setPackageBucket,
                    onBackground = viewModel::setPackageBackground,
                    onSetComponentEnabled = viewModel::setComponentEnabled,
                    onLaunchComponent = viewModel::launchComponent,
                    onSetRuntimePermission = viewModel::setRuntimePermission,
                    onSetAppOpMode = viewModel::setAppOpMode,
                    onLoadAppOps = viewModel::loadPermissionAppOps,
                    onLoadRuntime = viewModel::loadAppRuntime,
                    onExportDiagnostic = viewModel::exportAppControlDiagnostic,
                )
            }
            composable(RootToolsDestination.AD_GOVERNANCE.route) {
                AdGovernanceRoute(onBack = back)
            }
            composable(RootToolsDestination.COMPONENTS.route) {
                ComponentManagerScreen(
                    state = state,
                    onBack = back,
                    onLoad = viewModel::loadComponents,
                    onSetEnabled = viewModel::setComponentEnabled,
                )
            }
            composable(RootToolsDestination.PERMISSION_OPS.route) {
                PermissionAppOpsScreen(
                    state = state,
                    onBack = back,
                    onLoad = viewModel::loadPermissionAppOps,
                    onSetMode = viewModel::setAppOpMode,
                )
            }
        }

        navigation(
            route = RootToolsTab.DEVICE.graphRoute,
            startDestination = RootToolsDestination.DEVICE.route,
        ) {
            composable(RootToolsDestination.DEVICE.route) {
                DomainLandingScreen(RootToolsTab.DEVICE, state, open)
            }
            composable(RootToolsDestination.PERFORMANCE.route) {
                PerformanceScreen(
                    state = state.toPerformanceUiState(),
                    onBack = back,
                    onRefresh = viewModel::refresh,
                    onModeSelected = viewModel::setMode,
                    onReleaseCaps = viewModel::releaseRootToolsCpuCaps,
                )
            }
            composable(RootToolsDestination.SHADOW_DISPLAY.route) {
                ShadowDisplayRoute(onBack = back)
            }
            composable(RootToolsDestination.AGENT_SESSION.route) {
                AgentSessionRoute(onBack = back)
            }
            composable(RootToolsDestination.ADB.route) {
                AdbScreen(
                    state = state,
                    onBack = back,
                    onRefresh = viewModel::loadAdb,
                    onAdbToggle = viewModel::toggleAdb,
                    onNativeWirelessToggle = viewModel::setNativeWireless,
                    onRootBootRestore = { viewModel.setAdbBootPolicy(restoreRootTcp = it) },
                    onNativeBootRestore = { viewModel.setAdbBootPolicy(restoreNativeWireless = it) },
                )
            }
            composable(RootToolsDestination.NETWORK.route) {
                NetworkDiagnosticsScreen(
                    state = state,
                    onBack = back,
                    onRefresh = viewModel::loadNetwork,
                    onPing = viewModel::pingNetworkTarget,
                )
            }
            composable(RootToolsDestination.STORAGE.route) {
                StorageDiagnosticsScreen(state, back, viewModel::loadStorage)
            }
            composable(RootToolsDestination.BATTERY.route) {
                BatteryThermalScreen(
                    state = state,
                    onBack = back,
                    onProtectionToggle = viewModel::setBatteryProtection,
                    onSamplingSeconds = viewModel::setDetailSamplingSeconds,
                )
            }
        }

        navigation(
            route = RootToolsTab.DIAGNOSTICS.graphRoute,
            startDestination = RootToolsDestination.DIAGNOSTICS.route,
        ) {
            composable(RootToolsDestination.DIAGNOSTICS.route) {
                DomainLandingScreen(RootToolsTab.DIAGNOSTICS, state, open)
            }
            composable(RootToolsDestination.HEALTH_DASHBOARD.route) {
                HealthDashboardScreen(
                    state = state.toHealthDashboardUiState(),
                    onBack = back,
                    onRefresh = viewModel::refresh,
                    onSamplingSeconds = viewModel::setDetailSamplingSeconds,
                )
            }
            composable(RootToolsDestination.PROCESS_DIAGNOSTICS.route) {
                DiagnosticsScreen(
                    state = state,
                    onBack = back,
                    onRefresh = viewModel::loadDiagnostics,
                    onAttributeRootShell = viewModel::attributeRootShell,
                )
            }
            composable(RootToolsDestination.INTEGRITY.route) {
                EnvironmentIntegrityRoute(onBack = back)
            }
        }

        navigation(
            route = RootToolsTab.SYSTEM.graphRoute,
            startDestination = RootToolsDestination.SYSTEM.route,
        ) {
            composable(RootToolsDestination.SYSTEM.route) {
                DomainLandingScreen(RootToolsTab.SYSTEM, state, open)
            }
            composable(RootToolsDestination.MODULES.route) {
                ModuleCenterScreen(
                    state = state,
                    onBack = back,
                    onRefresh = viewModel::loadModules,
                    onMagiskToggle = viewModel::setMagiskModuleEnabled,
                    onVectorToggle = viewModel::setVectorModuleEnabled,
                    onLoadScope = viewModel::loadVectorScope,
                )
            }
            composable(RootToolsDestination.ACTIONS.route) {
                CommonActionsScreen(
                    state = state,
                    onBack = back,
                    onRunAction = viewModel::runSystemAction,
                    onModeSelected = viewModel::setMode,
                    onExportDiagnostic = viewModel::exportDiagnosticReport,
                    onFavorite = viewModel::setActionFavorite,
                )
            }
            composable(RootToolsDestination.SHIZUKU.route) {
                ShizukuSuiScreen(
                    state = state,
                    onBack = back,
                    onRefresh = viewModel::refreshShizuku,
                    onRequestPermission = viewModel::requestShizukuPermission,
                    onSelfTest = viewModel::runShizukuSelfTest,
                )
            }
            composable(RootToolsDestination.DEVELOPER_RUNTIME.route) {
                DeveloperRuntimeRoute(onBack = back)
            }
        }
    }
}

private fun navigateToTab(navController: NavController, tab: RootToolsTab) {
    navController.navigate(tab.graphRoute) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun navigateToDestination(navController: NavController, destination: RootToolsDestination) {
    navigateToTab(navController, destination.tab)
    if (destination.route != destination.tab.landingRoute) {
        navController.navigate(destination.route) {
            launchSingleTop = true
        }
    }
}

private fun diagnosticAttentionCount(state: DashboardUiState): Int {
    val abnormalRootShells = maxOf(
        state.diagnostics.abnormalRootShells,
        if (state.health.abnormalRootShell != null) 1 else 0,
    )
    return HomeHealthPolicy.decide(
        HomeHealthInput(
            rootAvailable = state.snapshot.rootAvailable,
            metricsAvailable = state.health.rootAvailable,
            cpuUsagePercent = state.health.cpuUsagePercent,
            thermalStatus = state.health.thermal.status,
            skinC = state.health.thermal.skinC ?: state.snapshot.skinTempC,
            abnormalRootShells = abnormalRootShells,
            memoryStatus = state.health.memory.status,
        ),
    ).attention.size
}

private fun RootToolsTab.labelRes(): Int = when (this) {
    RootToolsTab.HOME -> R.string.nav_home
    RootToolsTab.APPS -> R.string.nav_apps
    RootToolsTab.DEVICE -> R.string.nav_device
    RootToolsTab.DIAGNOSTICS -> R.string.nav_diagnostics
    RootToolsTab.SYSTEM -> R.string.nav_system
}

private fun RootToolsTab.icon(): ImageVector = when (this) {
    RootToolsTab.HOME -> Icons.Rounded.Home
    RootToolsTab.APPS -> Icons.Rounded.Apps
    RootToolsTab.DEVICE -> Icons.Rounded.Devices
    RootToolsTab.DIAGNOSTICS -> Icons.Rounded.Terminal
    RootToolsTab.SYSTEM -> Icons.Rounded.Settings
}
