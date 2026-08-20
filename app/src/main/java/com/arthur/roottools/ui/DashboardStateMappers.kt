package com.arthur.roottools.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import com.arthur.roottools.feature.dashboard.presentation.HealthDashboardUiState
import com.arthur.roottools.feature.performance.presentation.PerformanceUiState
import com.arthur.roottools.model.AdbSnapshot
import com.arthur.roottools.model.DeviceHealthSnapshot
import com.arthur.roottools.model.DeviceSnapshot

internal fun mergeHealthIntoSnapshot(
    snapshot: DeviceSnapshot,
    health: DeviceHealthSnapshot,
): DeviceSnapshot {
    if (!snapshot.rootAvailable || !health.rootAvailable) return snapshot
    val healthByPolicy = health.cpuClusters.associateBy { it.policyId }
    val clusters = snapshot.cpuClusters.map { cluster ->
        val current = healthByPolicy[cluster.policyId] ?: return@map cluster
        cluster.copy(
            scalingMinKHz = current.scalingMinKHz,
            scalingMaxKHz = current.scalingMaxKHz,
            currentKHz = current.currentKHz,
        )
    }
    return snapshot.copy(
        thermalStatus = health.thermal.status,
        apTempC = health.thermal.apC,
        skinTempC = health.thermal.skinC,
        batteryTempC = health.thermal.batteryC,
        batteryLevel = health.battery.level,
        charging = health.battery.charging,
        cpuClusters = clusters,
    )
}

internal fun mergeAdbIntoSnapshot(snapshot: DeviceSnapshot, adb: AdbSnapshot): DeviceSnapshot = snapshot.copy(
    adbPort = adb.rootTcpPort,
    adbListening = adb.rootTcpListening,
    tailscaleIpv4 = adb.tailscaleIpv4 ?: snapshot.tailscaleIpv4,
)

internal fun notificationsGranted(application: Application): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return application.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

internal fun DashboardUiState.toHealthDashboardUiState(): HealthDashboardUiState = HealthDashboardUiState(
    loading = loading,
    health = health,
    healthHistory = healthHistory,
    dailyHealthHistory = dailyHealthHistory,
    detailSamplingSeconds = detailSamplingSeconds,
)

internal fun DashboardUiState.toPerformanceUiState(): PerformanceUiState = PerformanceUiState(
    loading = loading,
    actionInProgress = actionInProgress,
    snapshot = snapshot,
    mode = mode,
    cpuCapStates = cpuCapStates,
    cpuPolicyEvents = cpuPolicyEvents,
    actionMessage = actionMessage,
    error = error,
)
