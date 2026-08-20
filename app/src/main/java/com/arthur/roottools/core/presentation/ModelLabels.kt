package com.arthur.roottools.core.presentation

import androidx.annotation.StringRes
import com.arthur.roottools.R
import com.arthur.roottools.model.CpuCapSource
import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.model.ThermalStage

@StringRes
fun PerformanceMode.labelRes(): Int = when (this) {
    PerformanceMode.AUTO -> R.string.performance_mode_auto
    PerformanceMode.COOL -> R.string.performance_mode_cool
    PerformanceMode.PERFORMANCE -> R.string.performance_mode_performance
}

@StringRes
fun ThermalStage.labelRes(): Int = when (this) {
    ThermalStage.NORMAL -> R.string.thermal_stage_normal
    ThermalStage.WARM -> R.string.thermal_stage_warm
    ThermalStage.MODERATE -> R.string.thermal_stage_moderate
    ThermalStage.SEVERE -> R.string.thermal_stage_severe
}

@StringRes
fun CpuCapSource.labelRes(): Int = when (this) {
    CpuCapSource.FULL -> R.string.cpu_cap_source_full
    CpuCapSource.ROOT_TOOLS -> R.string.cpu_cap_source_root_tools
    CpuCapSource.SAMSUNG_THERMAL -> R.string.cpu_cap_source_samsung_thermal
    CpuCapSource.OTHER_SYSTEM -> R.string.cpu_cap_source_other_system
}
