package com.aibox.backgroundserver.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import com.aibox.backgroundserver.MainActivity
import com.aibox.backgroundserver.domain.RuntimeMetrics
import com.aibox.backgroundserver.domain.TunnelRuntimeState
import com.aibox.backgroundserver.engine.wireguard.WireGuardRuntime
import com.aibox.backgroundserver.platform.power.TelemetryReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BackgroundRuntimeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var samplerJob: Job? = null
    private var wireGuardStatsJob: Job? = null
    private var startedRealtime = 0L
    private var startedWallClock = 0L
    private var accumulatedWh = 0.0
    private var wakeLockAcquiredRealtime = 0L
    private lateinit var preferences: RuntimePreferences
    private val wireGuard by lazy { WireGuardRuntime.get(this) }
    private val engineSupervisor by lazy { EngineSupervisor(listOf(wireGuard)) }

    override fun onCreate() {
        super.onCreate()
        preferences = RuntimePreferences(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                preferences.screenOffWorkEnabled = true
                ensureRuntime()
            }
            ACTION_STOP_KEEPALIVE -> {
                preferences.screenOffWorkEnabled = false
                stopRuntimeIfIdle()
            }
            ACTION_START_WIREGUARD -> startWireGuardWork()
            ACTION_STOP_WIREGUARD -> stopWireGuardWork()
            ACTION_STOP_ALL -> stopAllWork()
            ACTION_RESTORE, null -> restoreRequestedWork()
            else -> restoreRequestedWork()
        }
        return START_STICKY
    }

    private fun ensureRuntime() {
        if (wakeLock?.isHeld == true) return
        startForeground(NOTIFICATION_ID, notification(notificationText()))
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:background-runtime",
        ).apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
        startedRealtime = SystemClock.elapsedRealtime()
        wakeLockAcquiredRealtime = startedRealtime
        startedWallClock = System.currentTimeMillis()
        accumulatedWh = 0.0
        samplerJob = scope.launch {
            var lastRealtime = SystemClock.elapsedRealtime()
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                renewWakeLockIfNeeded(now)
                val sample = TelemetryReader.sample(this@BackgroundRuntimeService)
                val elapsedHours = (now - lastRealtime).coerceAtLeast(0L) / 3_600_000.0
                sample.watts?.let { accumulatedWh += it * elapsedHours }
                lastRealtime = now
                RuntimeMetricsStore.update(
                    RuntimeMetrics(
                        running = true,
                        startedAtMillis = startedWallClock,
                        runtimeMillis = now - startedRealtime,
                        instantaneousWatts = sample.watts,
                        accumulatedWh = accumulatedWh,
                        cpuLoadPercent = sample.cpuLoadPercent,
                        loadAverage1 = sample.loadAverage1,
                        loadAverage5 = sample.loadAverage5,
                        loadAverage15 = sample.loadAverage15,
                        memoryUsedPercent = sample.memoryUsedPercent,
                        totalRxBytes = sample.totalRxBytes,
                        totalTxBytes = sample.totalTxBytes,
                        temperatureCelsius = sample.temperatureCelsius,
                        batteryCharging = sample.batteryCharging,
                    ),
                )
                delay(1_000)
            }
        }
    }

    private fun restoreRequestedWork() {
        if (!preferences.screenOffWorkEnabled && !preferences.wireGuardRequested) {
            stopSelf()
            return
        }
        ensureRuntime()
        if (preferences.wireGuardRequested) startWireGuardEngine()
    }

    private fun startWireGuardWork() {
        preferences.wireGuardRequested = true
        ensureRuntime()
        startWireGuardEngine()
    }

    private fun startWireGuardEngine() {
        if (wireGuard.state.value.runtimeState == TunnelRuntimeState.RUNNING ||
            wireGuard.state.value.runtimeState == TunnelRuntimeState.STARTING
        ) return
        scope.launch(Dispatchers.IO) {
            val result = engineSupervisor.start(ENGINE_WIREGUARD)
            if (result.isFailure) preferences.wireGuardRequested = false
            updateForegroundNotification()
            if (result.isSuccess) startWireGuardStatsLoop() else stopRuntimeIfIdle()
        }
    }

    private fun startWireGuardStatsLoop() {
        wireGuardStatsJob?.cancel()
        wireGuardStatsJob = scope.launch(Dispatchers.IO) {
            while (isActive && wireGuard.state.value.runtimeState == TunnelRuntimeState.RUNNING) {
                wireGuard.updateStatistics()
                delay(2_000)
            }
        }
    }

    private fun stopWireGuardWork() {
        preferences.wireGuardRequested = false
        wireGuardStatsJob?.cancel()
        wireGuardStatsJob = null
        scope.launch(Dispatchers.IO) {
            engineSupervisor.stop(ENGINE_WIREGUARD)
            updateForegroundNotification()
            stopRuntimeIfIdle()
        }
    }

    private fun stopRuntimeIfIdle() {
        if (preferences.screenOffWorkEnabled || preferences.wireGuardRequested) {
            updateForegroundNotification()
            return
        }
        shutdownRuntime()
    }

    private fun stopAllWork() {
        preferences.screenOffWorkEnabled = false
        preferences.wireGuardRequested = false
        wireGuardStatsJob?.cancel()
        wireGuardStatsJob = null
        scope.launch(Dispatchers.IO) {
            runCatching { engineSupervisor.stop(ENGINE_WIREGUARD) }
            shutdownRuntime()
        }
    }

    private fun renewWakeLockIfNeeded(nowRealtime: Long) {
        if (nowRealtime - wakeLockAcquiredRealtime < WAKE_LOCK_RENEW_MS) return
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
            lock.acquire(WAKE_LOCK_TIMEOUT_MS)
            wakeLockAcquiredRealtime = nowRealtime
        }
    }

    private fun shutdownRuntime() {
        samplerJob?.cancel()
        samplerJob = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        RuntimeMetricsStore.update(RuntimeMetrics())
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        samplerJob?.cancel()
        wireGuardStatsJob?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        RuntimeMetricsStore.update(RuntimeMetrics())
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "后台工作",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "保持后台服务器任务持续运行"
            },
        )
    }

    private fun notification(text: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Background Server")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    1,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    "停止后台工作",
                    PendingIntent.getService(
                        this,
                        2,
                        Intent(this, BackgroundRuntimeService::class.java).setAction(ACTION_STOP_ALL),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).build(),
            )
            .build()

    private fun updateForegroundNotification() {
        if (wakeLock?.isHeld != true) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(notificationText()))
    }

    private fun notificationText(): String = when {
        preferences.wireGuardRequested && preferences.screenOffWorkEnabled -> "WireGuard 与息屏后台工作正在运行"
        preferences.wireGuardRequested -> "WireGuard 后台代理正在运行"
        else -> "息屏后台工作已启用"
    }

    companion object {
        private const val CHANNEL_ID = "background-runtime"
        private const val NOTIFICATION_ID = 1001
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        private const val WAKE_LOCK_RENEW_MS = 9 * 60 * 1000L
        private const val ACTION_START = "com.aibox.backgroundserver.START"
        private const val ACTION_STOP_KEEPALIVE = "com.aibox.backgroundserver.STOP_KEEPALIVE"
        private const val ACTION_START_WIREGUARD = "com.aibox.backgroundserver.START_WIREGUARD"
        private const val ACTION_STOP_WIREGUARD = "com.aibox.backgroundserver.STOP_WIREGUARD"
        private const val ACTION_STOP_ALL = "com.aibox.backgroundserver.STOP_ALL"
        private const val ACTION_RESTORE = "com.aibox.backgroundserver.RESTORE"
        private const val ENGINE_WIREGUARD = "wireguard"

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, BackgroundRuntimeService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, BackgroundRuntimeService::class.java).setAction(ACTION_STOP_KEEPALIVE),
            )
        }

        fun startWireGuard(context: Context) {
            context.startForegroundService(
                Intent(context, BackgroundRuntimeService::class.java).setAction(ACTION_START_WIREGUARD),
            )
        }

        fun stopWireGuard(context: Context) {
            context.startService(
                Intent(context, BackgroundRuntimeService::class.java).setAction(ACTION_STOP_WIREGUARD),
            )
        }

        fun restore(context: Context) {
            context.startForegroundService(
                Intent(context, BackgroundRuntimeService::class.java).setAction(ACTION_RESTORE),
            )
        }
    }
}
