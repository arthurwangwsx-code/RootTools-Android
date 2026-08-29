package com.aibox.backgroundserver.ui

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aibox.backgroundserver.AppViewModel
import com.aibox.backgroundserver.ui.home.HomeScreen
import com.aibox.backgroundserver.ui.network.NetworkScreen
import com.aibox.backgroundserver.ui.network.ProxyScreen
import com.aibox.backgroundserver.ui.power.PowerScreen
import com.aibox.backgroundserver.ui.power.ScreenWakeScreen
import com.aibox.backgroundserver.ui.theme.BackgroundServerTheme

private enum class Route { HOME, POWER, SCREEN_WAKE, NETWORK, PROXY }

@Composable
fun BackgroundServerApp(viewModel: AppViewModel) {
    val root by viewModel.rootStatus.collectAsStateWithLifecycle()
    val power by viewModel.powerSettings.collectAsStateWithLifecycle()
    val metrics by viewModel.runtimeMetrics.collectAsStateWithLifecycle()
    val network by viewModel.network.collectAsStateWithLifecycle()
    val networkCapabilities by viewModel.networkCapabilities.collectAsStateWithLifecycle()
    val wireGuard by viewModel.wireGuardState.collectAsStateWithLifecycle()
    val softBlanked by viewModel.softBlanked.collectAsStateWithLifecycle()
    var route by remember { mutableStateOf(Route.HOME) }

    BackHandler(enabled = route != Route.HOME) { route = Route.HOME }

    BackgroundServerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                when (route) {
                    Route.HOME -> HomeScreen(
                        rootStatus = root,
                        metrics = metrics,
                        network = network,
                        wireGuard = wireGuard,
                        onPower = { route = Route.POWER },
                        onNetwork = { route = Route.NETWORK },
                        onProxy = { route = Route.PROXY },
                    )
                    Route.POWER -> PowerScreen(
                        rootStatus = root,
                        settings = power,
                        metrics = metrics,
                        onBack = { route = Route.HOME },
                        onScreenWake = { route = Route.SCREEN_WAKE },
                        onScreenOffWork = viewModel::setScreenOffWork,
                        onRestoreAfterBoot = viewModel::setRestoreAfterBoot,
                        onSleepNow = viewModel::blankOrSleepDisplay,
                    )
                    Route.SCREEN_WAKE -> ScreenWakeScreen(
                        settings = power,
                        onBack = { route = Route.POWER },
                        onDoubleTapToWake = viewModel::setDoubleTapToWake,
                        onScreenOffWithoutLock = viewModel::setScreenOffWithoutLock,
                        onSleepNow = viewModel::blankOrSleepDisplay,
                        onWakeNow = viewModel::wakeDisplay,
                    )
                    Route.NETWORK -> NetworkScreen(
                        rootStatus = root,
                        snapshot = network,
                        onBack = { route = Route.HOME },
                        onRefresh = viewModel::refreshNetwork,
                        onProxy = { route = Route.PROXY },
                    )
                    Route.PROXY -> ProxyScreen(
                        capabilities = networkCapabilities,
                        network = network,
                        wireGuard = wireGuard,
                        onBack = { route = Route.HOME },
                        onRefresh = {
                            viewModel.refreshNetwork()
                            viewModel.refreshNetworkCapabilities()
                        },
                        vpnPermissionIntent = viewModel::wireGuardVpnPermissionIntent,
                        onStart = viewModel::startWireGuardServer,
                        onStop = viewModel::stopWireGuardServer,
                    )
                }
                SoftBlankOverlay(
                    visible = softBlanked,
                    onDoubleTap = viewModel::restoreSoftBlank,
                )
            }
        }
    }
}

@Composable
private fun SoftBlankOverlay(
    visible: Boolean,
    onDoubleTap: () -> Unit,
) {
    val activity = LocalActivity.current

    DisposableEffect(visible, activity) {
        val window = activity?.window
        if (visible && window != null) {
            val params = window.attributes
            params.screenBrightness = 0f
            window.attributes = params
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.getInsetsController(window, window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) {
                val params = window.attributes
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = params
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1000f)
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onDoubleTap() })
                },
        )
    }
}
