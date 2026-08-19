package com.arthur.roottools.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.arthur.roottools.MainActivity
import com.arthur.roottools.R
import com.arthur.roottools.data.DeviceRepository
import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.model.CpuPolicyEventType
import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.model.ThermalStage
import com.arthur.roottools.policy.CpuPolicyController
import com.arthur.roottools.policy.CpuPolicyEventStore
import com.arthur.roottools.policy.PolicyStore
import com.arthur.roottools.policy.ThermalStageHysteresis
import com.arthur.roottools.root.RootShell
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
    private val thermalHysteresis = ThermalStageHysteresis()
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val shell = RootShell()
        repository = DeviceRepository(shell)
        store = PolicyStore(this)
        eventStore = CpuPolicyEventStore(this)
        controller = CpuPolicyController(
            shell = shell,
            store = store,
            eventStore = eventStore,
            auditStore = RootActionAuditStore(this),
            auditSource = "CpuPolicyService",
        )
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

        startForeground(NOTIFICATION_ID, buildNotification("正在读取设备状态…"))
        when (store.mode) {
            PerformanceMode.COOL -> applyStaticCool()
            PerformanceMode.AUTO,
            PerformanceMode.PERFORMANCE,
            -> startMonitor(forceRestart = explicitMode != null)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun applyStaticCool() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            val snapshot = repository.readSnapshot()
            val policy = controller.apply(PerformanceMode.COOL, snapshot)
            val summary = if (policy != null) {
                "Cool · ${snapshot.apTempC?.let { "AP %.1f°C".format(it) } ?: "已限峰"}"
            } else {
                "Cool 应用失败，请检查 Root 授权"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification(summary))
            delay(1_200)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

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

                val temp = snapshot.apTempC?.let { "AP %.1f°C".format(it) } ?: "温度读取中"
                val subtitle = when (mode) {
                    PerformanceMode.AUTO -> "Auto · ${effectiveStage.displayName} · $temp"
                    PerformanceMode.COOL -> "Cool · $temp"
                    PerformanceMode.PERFORMANCE -> {
                        val minutes = ((store.performanceUntilMs - System.currentTimeMillis()).coerceAtLeast(0L) / 60_000L) + 1
                        if (effectiveStage >= ThermalStage.MODERATE) {
                            "Performance · 热保护 ${effectiveStage.displayName} · $temp"
                        } else {
                            "Performance · 剩余 ${minutes}m · $temp"
                        }
                    }
                }
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification(subtitle))
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "CPU 策略守护",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "以低频采样根据温度调整 CPU 峰值，不关闭系统温控"
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_speed)
        .setContentTitle("Root Tools · CPU 策略")
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
        private const val POLL_INTERVAL_MS = 30_000L
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

