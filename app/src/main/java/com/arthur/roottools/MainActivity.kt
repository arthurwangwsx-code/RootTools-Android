package com.arthur.roottools

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.arthur.roottools.app.navigation.RootToolsAppShell
import com.arthur.roottools.ui.DashboardViewModel
import com.arthur.roottools.ui.theme.RootToolsTheme

class MainActivity : ComponentActivity() {
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private var requestedScreen by mutableStateOf<String?>(null)
    private var requestedScreenVersion by mutableLongStateOf(0L)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        dashboardViewModel.bootstrap()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeExternalScreen(intent)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            RootToolsTheme {
                key(requestedScreenVersion) {
                    RootToolsAppShell(
                        viewModel = dashboardViewModel,
                        initialScreen = requestedScreen,
                        initialScreenRequestVersion = requestedScreenVersion,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeExternalScreen(intent)
    }

    override fun onStart() {
        super.onStart()
        dashboardViewModel.setAppForeground(true)
    }

    override fun onStop() {
        dashboardViewModel.setAppForeground(false)
        super.onStop()
    }

    override fun onPostResume() {
        super.onPostResume()
        if (!needsNotificationPermission()) {
            dashboardViewModel.bootstrap()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (needsNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun consumeExternalScreen(intent: android.content.Intent?) {
        val screen = intent?.getStringExtra(EXTRA_OPEN_SCREEN) ?: return
        requestedScreen = screen
        requestedScreenVersion += 1L
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    companion object {
        const val EXTRA_OPEN_SCREEN = "open_screen"
        const val SCREEN_ADB = "adb"
        const val SCREEN_INTEGRITY = "integrity"
        const val SCREEN_AGENT_SESSION = "agent-session"
    }

}

