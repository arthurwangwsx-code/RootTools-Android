package com.arthur.roottools.feature.dashboard.presentation

import com.arthur.roottools.model.DeviceHealthSnapshot
import com.arthur.roottools.model.HealthHistoryPoint

data class HealthDashboardUiState(
    val loading: Boolean = false,
    val health: DeviceHealthSnapshot = DeviceHealthSnapshot(),
    val healthHistory: List<HealthHistoryPoint> = emptyList(),
    val dailyHealthHistory: List<HealthHistoryPoint> = emptyList(),
    val detailSamplingSeconds: Int = 2,
)
