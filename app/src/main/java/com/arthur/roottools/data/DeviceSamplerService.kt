package com.arthur.roottools.data

import android.content.Context
import com.arthur.roottools.model.DeviceHealthSnapshot
import com.arthur.roottools.model.HealthHistoryPoint
import com.arthur.roottools.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Process-local sampling coordinator. It intentionally is not an Android foreground Service:
 * Root Tools should not create another always-on notification just to render a dashboard.
 */
class DeviceSamplerService(
    context: Context,
    private val collector: DeviceHealthCollector = DeviceHealthCollector(RootShell()),
    private val preferences: SamplingPreferenceStore = SamplingPreferenceStore(context),
    private val persistentHistory: HealthHistoryStore = HealthHistoryStore(context),
) {
    private val _snapshot = MutableStateFlow(DeviceHealthSnapshot())
    val snapshot: StateFlow<DeviceHealthSnapshot> = _snapshot.asStateFlow()

    private val history = ArrayDeque<HealthHistoryPoint>()
    private var samplingJob: Job? = null
    private var intervalMs: Long = HOME_INTERVAL_MS
    private var dashboardActive: Boolean = false
    private var detailIntervalMs: Long = preferences.detailSeconds * 1_000L
    private var lastProcessSampleMs: Long = 0L

    fun start(scope: CoroutineScope, dashboardActive: Boolean = false) {
        this.dashboardActive = dashboardActive
        intervalMs = if (dashboardActive) detailIntervalMs else HOME_INTERVAL_MS
        restart(scope)
    }

    fun setDashboardActive(scope: CoroutineScope, active: Boolean) {
        dashboardActive = active
        val newInterval = if (active) detailIntervalMs else HOME_INTERVAL_MS
        if (newInterval == intervalMs && samplingJob?.isActive == true) return
        intervalMs = newInterval
        restart(scope)
    }

    fun setDetailIntervalSeconds(scope: CoroutineScope, seconds: Int) {
        require(seconds in SamplingPreferenceStore.ALLOWED_SECONDS)
        preferences.detailSeconds = seconds
        detailIntervalMs = seconds * 1_000L
        if (dashboardActive) {
            intervalMs = detailIntervalMs
            restart(scope)
        }
    }

    fun detailIntervalSeconds(): Int = (detailIntervalMs / 1_000L).toInt()

    fun stop() {
        samplingJob?.cancel()
        samplingJob = null
    }

    fun historySnapshot(): List<HealthHistoryPoint> = synchronized(history) { history.toList() }

    fun dailyHistorySnapshot(): List<HealthHistoryPoint> = persistentHistory.snapshot()

    private fun restart(scope: CoroutineScope) {
        samplingJob?.cancel()
        samplingJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val includeProcesses = now - lastProcessSampleMs >= PROCESS_INTERVAL_MS
                val sample = collector.collect(includeProcesses)
                val previous = _snapshot.value
                val merged = sample.copy(
                    topProcesses = sample.topProcesses.ifEmpty { previous.topProcesses },
                    topMemoryProcesses = sample.topMemoryProcesses.ifEmpty { previous.topMemoryProcesses },
                    lmk = if (sample.lmk.recentKillCount == 0 && sample.lmk.config.isEmpty() && previous.lmk.recentKillCount > 0) previous.lmk else sample.lmk,
                )
                _snapshot.value = merged
                if (includeProcesses) lastProcessSampleMs = now
                appendHistory(merged)
                delay(intervalMs)
            }
        }
    }

    private fun appendHistory(snapshot: DeviceHealthSnapshot) {
        if (!snapshot.rootAvailable) return
        val point = HealthHistoryPoint(
            timestampMs = snapshot.timestampMs,
            cpuUsagePercent = snapshot.cpuUsagePercent,
            memoryAvailableKb = snapshot.memory.availableKb,
            apTempC = snapshot.thermal.apC,
            skinTempC = snapshot.thermal.skinC,
            batteryLevel = snapshot.battery.level,
            thermalStatus = snapshot.thermal.status,
            clusterCurrentKHz = snapshot.cpuClusters.associate { it.policyId to it.currentKHz },
        )
        synchronized(history) {
            history.addLast(point)
            while (history.size > MAX_HISTORY_POINTS) history.removeFirst()
        }
        persistentHistory.appendIfDue(point)
    }

    private companion object {
        const val HOME_INTERVAL_MS = 30_000L
        const val PROCESS_INTERVAL_MS = 10_000L
        const val MAX_HISTORY_POINTS = 900
    }
}
