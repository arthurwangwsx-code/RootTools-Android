package com.arthur.roottools.boot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.arthur.roottools.app.rootToolsContainer
import com.arthur.roottools.data.AdbPreferenceStore
import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.policy.AdbController
import com.arthur.roottools.root.RootShell
import com.arthur.roottools.service.CpuPolicyService
import com.arthur.roottools.widget.AdbWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AdbBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_USER_UNLOCKED, ACTION_RETRY)) return
        val appContext = context.applicationContext
        if (action != ACTION_RETRY && appContext.rootToolsContainer.lagForensicsStore.enabled) {
            runCatching { CpuPolicyService.ensureRunning(appContext, source = "boot-lag-forensics") }
        }
        val policy = AdbPreferenceStore(appContext).bootPolicy()
        if (!policy.restoreRootTcp && !policy.restoreNativeWireless) {
            AdbWidgetProvider.requestUpdate(appContext)
            return
        }

        val pending = goAsync()
        val attempt = intent.getIntExtra(EXTRA_ATTEMPT, 0)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val result = runCatching {
                appContext.rootToolsContainer.createAdbController("BootRestore").restoreConfigured()
            }.getOrNull()
            if (result?.success != true && attempt < MAX_RETRIES) {
                scheduleRetry(appContext, attempt + 1)
            }
            AdbWidgetProvider.requestUpdate(appContext)
            pending.finish()
        }
    }

    private fun scheduleRetry(context: Context, attempt: Int) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, AdbBootReceiver::class.java)
            .setAction(ACTION_RETRY)
            .putExtra(EXTRA_ATTEMPT, attempt)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            4100 + attempt,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val delayMs = when (attempt) {
            1 -> 15_000L
            2 -> 35_000L
            else -> 60_000L
        }
        alarm.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delayMs,
            pendingIntent,
        )
    }

    companion object {
        private const val ACTION_RETRY = "com.arthur.roottools.action.ADB_BOOT_RETRY"
        private const val EXTRA_ATTEMPT = "attempt"
        private const val MAX_RETRIES = 3
    }
}

