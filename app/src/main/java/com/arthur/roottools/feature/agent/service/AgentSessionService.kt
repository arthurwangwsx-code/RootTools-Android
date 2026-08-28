package com.arthur.roottools.feature.agent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.arthur.roottools.MainActivity
import com.arthur.roottools.R
import com.arthur.roottools.app.rootToolsContainer
import com.arthur.roottools.core.agent.AgentNotificationChannelKind
import com.arthur.roottools.core.agent.AgentOverlayMode
import com.arthur.roottools.core.agent.AgentSessionPolicy
import com.arthur.roottools.core.agent.AgentSessionState
import com.arthur.roottools.core.agent.AgentSessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AgentSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager by lazy { rootToolsContainer.agentSessionManager }
    private val shadowController by lazy { rootToolsContainer.createShadowDisplayController("AgentPresence") }
    private var previewJob: Job? = null
    private lateinit var overlay: AgentOverlayWindow
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        createChannels()
        overlay = AgentOverlayWindow(
            context = this,
            onToggle = manager::toggleOverlay,
            onPauseResume = {
                if (manager.state.value.status == AgentSessionStatus.PAUSED) manager.resume() else manager.pause()
            },
            onHide = { manager.setOverlayMode(AgentOverlayMode.HIDDEN) },
            onStop = manager::stop,
            onOpenDetails = ::openDetails,
        )
        scope.launch {
            manager.state.collectLatest(::renderState)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_RESUME -> {
                if (manager.state.value.status == AgentSessionStatus.PAUSED) manager.resume() else manager.pause()
            }
            ACTION_STOP -> manager.stop()
            ACTION_SHOW_OVERLAY -> manager.setOverlayMode(AgentOverlayMode.COLLAPSED)
            ACTION_OPEN_DETAILS -> openDetails()
        }
        val state = manager.state.value
        if (state.active) renderState(state)
        return START_STICKY
    }

    override fun onDestroy() {
        previewJob?.cancel()
        overlay.remove()
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun renderState(state: AgentSessionState) {
        if (!AgentSessionPolicy.shouldRunForeground(state)) {
            overlay.remove()
            previewJob?.cancel()
            if (foregroundStarted) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                foregroundStarted = false
            }
            stopSelf()
            return
        }

        val notification = buildNotification(state)
        if (!foregroundStarted) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
            foregroundStarted = true
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }

        overlay.render(state)
        updatePreviewLoop(state)
    }

    private fun updatePreviewLoop(state: AgentSessionState) {
        val shouldRefresh = AgentSessionPolicy.shouldRefreshPreview(state, Settings.canDrawOverlays(this))
        if (!shouldRefresh) {
            previewJob?.cancel()
            previewJob = null
            if (state.overlayMode != AgentOverlayMode.EXPANDED) overlay.updatePreview(null)
            return
        }
        if (previewJob?.isActive == true) return
        previewJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val latest = manager.state.value
                if (!AgentSessionPolicy.shouldRefreshPreview(latest, Settings.canDrawOverlays(this@AgentSessionService))) break
                val bytes = shadowController.capturePreview().getOrNull()
                withContext(Dispatchers.Main) { overlay.updatePreview(bytes) }
                delay(AgentSessionPolicy.PREVIEW_REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun buildNotification(state: AgentSessionState): Notification {
        val channelId = when (AgentSessionPolicy.notificationChannel(state)) {
            AgentNotificationChannelKind.RUNNING -> CHANNEL_RUNNING
            AgentNotificationChannelKind.ATTENTION -> CHANNEL_ATTENTION
        }
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_SCREEN, MainActivity.SCREEN_AGENT_SESSION)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseResume = PendingIntent.getService(
            this,
            2,
            Intent(this, AgentSessionService::class.java).setAction(ACTION_PAUSE_RESUME),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            3,
            Intent(this, AgentSessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val showOverlay = PendingIntent.getService(
            this,
            4,
            Intent(this, AgentSessionService::class.java).setAction(ACTION_SHOW_OVERLAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_speed)
            .setContentTitle(state.title.ifBlank { getString(R.string.agent_session_title) })
            .setContentText(state.currentStep.ifBlank { getString(R.string.agent_session_idle_step) })
            .setStyle(NotificationCompat.BigTextStyle().bigText(state.currentStep))
            .setOngoing(true)
            .setOnlyAlertOnce(state.status != AgentSessionStatus.WAITING_USER)
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(
                R.drawable.ic_speed,
                getString(if (state.status == AgentSessionStatus.PAUSED) R.string.agent_action_resume else R.string.agent_action_pause),
                pauseResume,
            )
            .addAction(R.drawable.ic_speed, getString(R.string.agent_action_stop), stop)

        if (Settings.canDrawOverlays(this) && state.overlayMode == AgentOverlayMode.HIDDEN) {
            builder.addAction(R.drawable.ic_speed, getString(R.string.agent_action_show_overlay), showOverlay)
        }
        state.targetLabel?.let(builder::setSubText)
        AgentSessionPolicy.normalizedProgress(state.progressCurrent, state.progressTotal)?.let { (current, total) ->
            builder.setProgress(total, current, false)
        }
        return builder.build()
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RUNNING,
                getString(R.string.agent_notification_channel_running),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.agent_notification_channel_running_desc)
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ATTENTION,
                getString(R.string.agent_notification_channel_attention),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.agent_notification_channel_attention_desc)
            }
        )
    }

    private fun openDetails() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_SCREEN, MainActivity.SCREEN_AGENT_SESSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    companion object {
        private const val NOTIFICATION_ID = 43021
        const val CHANNEL_RUNNING = "agent_session_running"
        const val CHANNEL_ATTENTION = "agent_session_attention"
        private const val ACTION_PAUSE_RESUME = "com.arthur.roottools.agent.PAUSE_RESUME"
        private const val ACTION_STOP = "com.arthur.roottools.agent.STOP"
        private const val ACTION_SHOW_OVERLAY = "com.arthur.roottools.agent.SHOW_OVERLAY"
        private const val ACTION_OPEN_DETAILS = "com.arthur.roottools.agent.OPEN_DETAILS"

        fun ensureRunning(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AgentSessionService::class.java))
        }

        fun stop(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            context.stopService(Intent(context, AgentSessionService::class.java))
        }
    }
}
