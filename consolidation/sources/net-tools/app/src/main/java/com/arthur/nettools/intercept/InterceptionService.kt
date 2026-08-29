package com.arthur.nettools.intercept

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.arthur.nettools.MainActivity
import com.arthur.nettools.capture.AppTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class InterceptionService : Service() {
    companion object {
        const val ACTION_START = "com.arthur.nettools.action.START_INTERCEPTION"
        const val ACTION_STOP = "com.arthur.nettools.action.STOP_INTERCEPTION"
        const val EXTRA_LABEL = "label"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_UID = "uid"
        const val EXTRA_BLOCK_QUIC = "block_quic"
        const val EXTRA_RESTART = "restart"
        const val EXTRA_FULL_PAYLOAD = "full_payload"
        private const val CHANNEL = "interception"
        private const val NOTIFICATION_ID = 4102
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var engine: InterceptionEngine

    override fun onCreate() {
        super.onCreate()
        engine = InterceptionEngine(applicationContext)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "TLS interception", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Keeps transparent TLS interception active in the background"
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> scope.launch {
                engine.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_START -> {
                val target = AppTarget(
                    intent.getStringExtra(EXTRA_LABEL).orEmpty(),
                    intent.getStringExtra(EXTRA_PACKAGE).orEmpty(),
                    intent.getIntExtra(EXTRA_UID, -1),
                )
                if (target.uid < 0 || target.packageName.isBlank()) return START_NOT_STICKY
                val options = InterceptionOptions(
                    blockQuic = intent.getBooleanExtra(EXTRA_BLOCK_QUIC, true),
                    restartTarget = intent.getBooleanExtra(EXTRA_RESTART, true),
                    fullPayload = intent.getBooleanExtra(EXTRA_FULL_PAYLOAD, true),
                )
                startForeground(NOTIFICATION_ID, notification("Starting ${target.label}"))
                scope.launch {
                    engine.start(target, options)
                    val state = InterceptionRuntime.state.value
                    getSystemService(NotificationManager::class.java).notify(
                        NOTIFICATION_ID,
                        notification(if (state.phase == InterceptionPhase.RUNNING) "Decrypting ${target.label}" else state.lastError ?: state.message),
                    )
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.launch { runCatching { engine.stop() } }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Net Tools · TLS interception")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
