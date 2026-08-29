package com.aibox.backgroundserver.runtime

import com.aibox.backgroundserver.domain.RuntimeMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RuntimeMetricsStore {
    private val _metrics = MutableStateFlow(RuntimeMetrics())
    val metrics: StateFlow<RuntimeMetrics> = _metrics.asStateFlow()

    fun update(metrics: RuntimeMetrics) {
        _metrics.value = metrics
    }
}
