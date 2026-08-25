package com.arthur.roottools.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.arthur.roottools.MainActivity
import com.arthur.roottools.R
import com.arthur.roottools.app.rootToolsContainer
import com.arthur.roottools.data.DeviceRepository
import com.arthur.roottools.data.LagForensicsMonitor
import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.model.CpuPolicyEventType
import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.model.ThermalStage
import com.arthur.roottools.policy.CpuPolicyController
import com.arthur.roottools.policy.CpuPolicyEventStore
import com.arthur.roottools.policy.CpuPolicyPollingPolicy
import com.arthur.roottools.policy.PolicyStore
import com.arthur.roottools.policy.ThermalStageHysteresis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CpuPolicyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: DeviceRepository
    private lateinit var store: PolicyStore
    private lateinit var controller: CpuPolicyController
    private lateinit var eventStore: CpuPolicyEventStore
    private lateinit var lagForensicsMonitor: LagForensicsMonitor
    private lateinit var powerManager: PowerManager
    private val thermalHysteresis = ThermalStageHysteresis()
    private var monitorJob: Job? = null
    private var lastNotificationText: String? = null

    override fun onCreate() {
        super.onCreate()
        val container = applicationContext.rootToolsContainer
        repository = container.deviceRepository
        store = container.policyStore
        eventStore = container.policyEventStore
        controller = container.createCpuPolicyController("CpuPolicyService")
        lagForensicsMonitor = container.lagForensicsMonitor
        powerManager = getSystemService(PowerManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val source = intent?.getStringExtra(EXTRA_SOURCE).orEmpty().ifBlank { "service" }
        val explicitMode = intent?.getStringExtra(EXTRA_MODE)?.let { raw ->
            runCatching { PerformanceMode.valueOf(raw) }.getOrNull()?.also { requested ->
                val previous = store.mode
                store.mode = requested
                store.performanceUntilMs = if (requested == PerformanceMode.PERFORMANCE) {
                    System.currentTimeMillis() + PERFORMANCE_DURATION_MS
                } else {
                    0L
                }
                if (previous != requested) {
                    eventStore.append(CpuPolicyEventType.MODE, "$source: ${previous.name}→${requested.name}")
                }
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.cpu_notification_reading)))
        startMonitor(forceRestart = explicitMode != null)
        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitor(forceRestart: Boolean = false) {
        if (forceRestart) {
            monitorJob?.cancel()
            monitorJob = null
        }
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                var mode = store.mode
                if (mode == PerformanceMode.PERFORMANCE &&
                    store.performanceUntilMs > 0L &&
                    System.currentTimeMillis() >= store.performanceUntilMs
                ) {
                    val previous = mode
                    mode = PerformanceMode.AUTO
                    store.mode = mode
                    store.performanceUntilMs = 0L
                    eventStore.append(CpuPolicyEventType.MODE, "timeout: ${previous.name}→AUTO")
                }

                val snapshot = repository.readSnapshot()
                controller.migrateLegacyCapsIfNeeded(snapshot)
                val stage = thermalHysteresis.update(snapshot.thermalStage())
                val effectiveStage = when (mode) {
                    PerformanceMode.AUTO -> stage
                    PerformanceMode.COOL -> maxOf(stage, ThermalStage.WARM)
                    PerformanceMode.PERFORMANCE -> if (stage >= ThermalStage.MODERATE) stage else ThermalStage.NORMAL
                }
                // Reconcile every poll. The controller itself only writes when it owns a cap or
                // needs to add a stricter one, so a stable state produces no sysfs writes.
                if (snapshot.rootAvailable) controller.apply(mode, snapshot, stage)
                lagForensicsMonitor.observe(snapshot, scope)

                val temp = snapshot.apTempC?.let { getString(R.string.cpu_temperature_ap, it) }
                    ?: getString(R.string.cpu_temperature_loading)
                val subtitle = when (mode) {
                    PerformanceMode.AUTO -> getString(R.string.cpu_notification_auto, effectiveStage.displayName, temp)
                    PerformanceMode.COOL -> getString(R.string.cpu_notification_cool, temp)
                    PerformanceMode.PERFORMANCE -> {
                        val minutes = ((store.performanceUntilMs - System.currentTimeMillis()).coerceAtLeast(0L) / 60_000L) + 1
                        if (effectiveStage >= ThermalStage.MODERATE) {
                            getString(R.string.cpu_notification_performance_thermal, effectiveStage.displayName, temp)
                        } else {
                            getString(R.string.cpu_notification_performance_remaining, minutes, temp)
                        }
                    }
                }
                if (subtitle != lastNotificationText) {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(subtitle))
                    lastNotificationText = subtitle
                }
                // Cadence follows the physical thermal stage, not the mode-adjusted effective stage.
                // Cool intentionally treats Normal as Warm for cap selection, but that must not turn
                // an otherwise stable device back into a 30s polling loop.
                delay(CpuPolicyPollingPolicy.intervalMs(mode, stage, powerManager.isInteractive))
            }
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.cpu_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.cpu_notification_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_speed)
        .setContentTitle(getString(R.string.cpu_notification_title))
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    companion object {
        private const val CHANNEL_ID = "cpu_policy"
        private const val NOTIFICATION_ID = 4101
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_SOURCE = "source"
        private const val PERFORMANCE_DURATION_MS = 15 * 60_000L

        fun setMode(context: Context, mode: PerformanceMode, source: String = "UI") {
            val intent = Intent(context, CpuPolicyService::class.java)
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_SOURCE, source)
            ContextCompat.startForegroundService(context, intent)
        }

        fun ensureRunning(context: Context, source: String = "bootstrap") {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CpuPolicyService::class.java).putExtra(EXTRA_SOURCE, source),
            )
        }
    }
}

