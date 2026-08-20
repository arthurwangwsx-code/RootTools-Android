package com.arthur.roottools.feature.performance.presentation

import com.arthur.roottools.model.CpuCapState
import com.arthur.roottools.model.CpuPolicyEvent
import com.arthur.roottools.model.DeviceSnapshot
import com.arthur.roottools.model.PerformanceMode

data class PerformanceUiState(
    val loading: Boolean = false,
    val actionInProgress: Boolean = false,
    val snapshot: DeviceSnapshot = DeviceSnapshot(),
    val mode: PerformanceMode = PerformanceMode.AUTO,
    val cpuCapStates: List<CpuCapState> = emptyList(),
    val cpuPolicyEvents: List<CpuPolicyEvent> = emptyList(),
    val actionMessage: String? = null,
    val error: String? = null,
)
