package com.arthur.roottools.feature.network.inspection.intercept

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.arthur.roottools.MainActivity
import com.arthur.roottools.R
import com.arthur.roottools.app.rootToolsContainer
import com.arthur.roottools.feature.network.inspection.capture.AppTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class InterceptionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var engine: InterceptionEngine

    override fun onCreate() {
        super.onCreate()
        engine = application.rootToolsContainer.createInterceptionEngine()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.network_interception_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.network_interception_notification_channel_desc)
            },
        )
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
                    label = intent.getStringExtra(EXTRA_LABEL).orEmpty(),
                    packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty(),
                    uid = intent.getIntExtra(EXTRA_UID, -1),
                )
                if (target.uid < 10_000 || !PACKAGE_NAME.matches(target.packageName)) return START_NOT_STICKY
                val options = InterceptionOptions(
                    blockQuic = intent.getBooleanExtra(EXTRA_BLOCK_QUIC, true),
                    restartTarget = intent.getBooleanExtra(EXTRA_RESTART, true),
                    fullPayload = intent.getBooleanExtra(EXTRA_FULL_PAYLOAD, true),
                    sslInsecureUpstream = intent.getBooleanExtra(EXTRA_INSECURE_UPSTREAM, true),
                )
                startForeground(
                    NOTIFICATION_ID,
                    notification(getString(R.string.network_interception_notification_starting, target.label)),
                )
                scope.launch {
                    engine.start(target, options)
                    val state = InterceptionRuntime.state.value
                    val text = if (state.phase == InterceptionPhase.RUNNING) {
                        getString(R.string.network_interception_notification_running, target.label)
                    } else {
                        getString(R.string.network_interception_notification_failed)
                    }
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
                    if (state.phase != InterceptionPhase.RUNNING) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (InterceptionRuntime.state.value.phase == InterceptionPhase.RUNNING) {
            PROCESS_CLEANUP_SCOPE.launch { runCatching { engine.stop() } }
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(getString(R.string.network_interception_notification_title))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val ACTION_START = "com.arthur.roottools.action.START_NETWORK_INTERCEPTION"
        private const val ACTION_STOP = "com.arthur.roottools.action.STOP_NETWORK_INTERCEPTION"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_UID = "uid"
        private const val EXTRA_BLOCK_QUIC = "block_quic"
        private const val EXTRA_RESTART = "restart"
        private const val EXTRA_FULL_PAYLOAD = "full_payload"
        private const val EXTRA_INSECURE_UPSTREAM = "insecure_upstream"
        private const val CHANNEL = "network_interception"
        private const val NOTIFICATION_ID = 4102
        private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
        private val PROCESS_CLEANUP_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun startIntent(context: Context, target: AppTarget, options: InterceptionOptions): Intent =
            Intent(context, InterceptionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LABEL, target.label)
                putExtra(EXTRA_PACKAGE, target.packageName)
                putExtra(EXTRA_UID, target.uid)
                putExtra(EXTRA_BLOCK_QUIC, options.blockQuic)
                putExtra(EXTRA_RESTART, options.restartTarget)
                putExtra(EXTRA_FULL_PAYLOAD, options.fullPayload)
                putExtra(EXTRA_INSECURE_UPSTREAM, options.sslInsecureUpstream)
            }

        fun stopIntent(context: Context): Intent = Intent(context, InterceptionService::class.java).apply {
            action = ACTION_STOP
        }
    }
}
