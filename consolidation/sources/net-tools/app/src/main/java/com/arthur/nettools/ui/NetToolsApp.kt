package com.arthur.nettools.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.arthur.nettools.MainViewModel
import com.arthur.nettools.ui.screens.AboutScreen
import com.arthur.nettools.ui.screens.CertificateScreen
import com.arthur.nettools.ui.screens.DashboardScreen
import com.arthur.nettools.ui.screens.DecryptScreen
import com.arthur.nettools.ui.screens.DecryptedEventDetailScreen
import com.arthur.nettools.ui.screens.DecryptSessionDetailScreen
import com.arthur.nettools.ui.screens.DiagnosticsScreen
import com.arthur.nettools.ui.screens.SessionDetailScreen
import com.arthur.nettools.ui.screens.PacketDetailScreen
import com.arthur.nettools.ui.screens.SettingsScreen
import com.arthur.nettools.ui.screens.TrafficScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetToolsApp(viewModel: MainViewModel) {
    val capture by viewModel.captureState.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val ca by viewModel.ca.collectAsStateWithLifecycle()
    val addon by viewModel.addon.collectAsStateWithLifecycle()
    val intercept by viewModel.interceptionState.collectAsStateWithLifecycle()
    val interceptHistory by viewModel.interceptionHistory.collectAsStateWithLifecycle()
    val inspectedIntercept by viewModel.inspectedInterception.collectAsStateWithLifecycle()
    val inspectedEvents by viewModel.inspectedEvents.collectAsStateWithLifecycle()
    val action by viewModel.actionMessage.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val backStack = remember { mutableStateListOf<Any>(DashboardRoute) }
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colorScheme = when {
        android.os.Build.VERSION.SDK_INT >= 31 && dark -> androidx.compose.material3.dynamicDarkColorScheme(context)
        android.os.Build.VERSION.SDK_INT >= 31 -> androidx.compose.material3.dynamicLightColorScheme(context)
        dark -> androidx.compose.material3.darkColorScheme()
        else -> androidx.compose.material3.lightColorScheme()
    }

    fun top(route: Any) {
        backStack.clear()
        backStack.add(route)
    }
    fun push(route: Any) { backStack.add(route) }
    fun back() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else top(DashboardRoute) }

    LaunchedEffect(action) {
        action?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    val current = backStack.last()
    val topLevel = when (current) {
        DashboardRoute -> TopDestination.DASHBOARD
        TrafficRoute -> TopDestination.TRAFFIC
        DecryptRoute -> TopDestination.DECRYPT
        SettingsRoute -> TopDestination.SETTINGS
        else -> null
    }
    val title = when (current) {
        DashboardRoute -> "Net Tools"
        TrafficRoute -> "Traffic"
        DecryptRoute -> "TLS Decryption"
        SettingsRoute -> "Settings"
        CertificateRoute -> "Certificate Manager"
        DiagnosticsRoute -> "Diagnostics"
        AboutRoute -> "About"
        is CaptureSessionRoute -> "Capture Session"
        is PacketDetailRoute -> "Packet #${current.packetId}"
        is DecryptedEventRoute -> "Decrypted Payload"
        is DecryptSessionRoute -> "Decryption Session"
        else -> "Net Tools"
    }

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (topLevel == null) {
                            IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        }
                    },
                )
            },
            bottomBar = {
                if (topLevel != null) {
                    NavigationBar {
                        TopDestination.entries.forEach { dest ->
                            val icon = when (dest) {
                                TopDestination.DASHBOARD -> Icons.Default.Home
                                TopDestination.TRAFFIC -> Icons.Default.NetworkCheck
                                TopDestination.DECRYPT -> Icons.Default.Https
                                TopDestination.SETTINGS -> Icons.Default.Settings
                            }
                            NavigationBarItem(
                                selected = topLevel == dest,
                                onClick = {
                                    top(when (dest) {
                                        TopDestination.DASHBOARD -> DashboardRoute
                                        TopDestination.TRAFFIC -> TrafficRoute
                                        TopDestination.DECRYPT -> DecryptRoute
                                        TopDestination.SETTINGS -> SettingsRoute
                                    })
                                },
                                icon = { Icon(icon, dest.label) },
                                label = { Text(dest.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavDisplay(
                backStack = backStack,
                onBack = ::back,
                modifier = Modifier.fillMaxSize().padding(padding),
                entryProvider = { key ->
                    NavEntry(key) {
                        when (key) {
                            DashboardRoute -> DashboardScreen(capture, intercept, ca, addon, onTraffic = { top(TrafficRoute) }, onDecrypt = { top(DecryptRoute) }, onCertificates = { push(CertificateRoute) })
                            TrafficRoute -> TrafficScreen(capture, apps, viewModel, onSession = { id -> viewModel.prepareCaptureSession(id); push(CaptureSessionRoute(id)) })
                            DecryptRoute -> DecryptScreen(
                                intercept, addon, ca, apps, interceptHistory, viewModel,
                                onCertificate = { push(CertificateRoute) },
                                onEvent = { push(DecryptedEventRoute(it)) },
                                onHistorySession = { id -> viewModel.inspectInterceptionSession(id); push(DecryptSessionRoute(id)) },
                            )
                            SettingsRoute -> SettingsScreen(ca, addon, intercept, onCertificates = { push(CertificateRoute) }, onDiagnostics = { push(DiagnosticsRoute) }, onAbout = { push(AboutRoute) })
                            CertificateRoute -> CertificateScreen(ca, addon, viewModel)
                            DiagnosticsRoute -> DiagnosticsScreen(capture, intercept, addon, ca, viewModel)
                            AboutRoute -> AboutScreen()
                            is CaptureSessionRoute -> SessionDetailScreen(capture.sessions.firstOrNull { it.id == key.id }, onPacket = { packetId -> push(PacketDetailRoute(key.id, packetId)) })
                            is PacketDetailRoute -> PacketDetailScreen(capture.sessions.firstOrNull { it.id == key.sessionId }?.analysis?.packets?.firstOrNull { it.id == key.packetId })
                            is DecryptedEventRoute -> DecryptedEventDetailScreen(intercept.recentEvents.firstOrNull { it.id == key.id })
                            is DecryptSessionRoute -> DecryptSessionDetailScreen(inspectedIntercept, inspectedEvents)
                            else -> Text("Unknown destination")
                        }
                    }
                },
            )
        }
    }
}
