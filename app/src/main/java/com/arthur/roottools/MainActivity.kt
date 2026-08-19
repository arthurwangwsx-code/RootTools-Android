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
import androidx.core.content.ContextCompat
import com.arthur.roottools.ui.DashboardRoute
import com.arthur.roottools.ui.DashboardViewModel
import com.arthur.roottools.ui.theme.RootToolsTheme

class MainActivity : ComponentActivity() {
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        dashboardViewModel.bootstrap()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            RootToolsTheme {
                DashboardRoute(dashboardViewModel)
            }
        }
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

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

}

