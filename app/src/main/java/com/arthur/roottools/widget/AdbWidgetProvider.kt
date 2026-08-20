package com.arthur.roottools.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.arthur.roottools.MainActivity
import com.arthur.roottools.R
import com.arthur.roottools.app.rootToolsContainer
import com.arthur.roottools.data.AdbPreferenceStore
import com.arthur.roottools.data.AdbRepository
import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.policy.AdbController
import com.arthur.roottools.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AdbWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        renderCached(context, appWidgetManager, appWidgetIds)
        refreshAsync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_ENABLE_ROOT_TCP) {
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val controller = context.rootToolsContainer.createAdbController("Widget")
                controller.setRootTcpEnabled(true)
                refreshNow(context.applicationContext)
                pending.finish()
            }
        }
    }

    private fun refreshAsync(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { refreshNow(context.applicationContext) }
    }

    private suspend fun refreshNow(context: Context) {
        context.rootToolsContainer.adbRepository.read()
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, AdbWidgetProvider::class.java))
        renderCached(context, manager, ids)
    }

    private fun renderCached(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val state = AdbPreferenceStore(context).lastKnown()
        val title = if (state.rootTcpEnabled) {
            context.getString(R.string.adb_tile_label_port, state.rootTcpPort)
        } else {
            context.getString(R.string.adb_tile_label_root)
        }
        val subtitle = when {
            state.rootTcpEnabled && state.tailscaleIpv4 != null -> state.tailscaleIpv4
            state.rootTcpEnabled -> context.getString(R.string.common_enabled)
            state.nativeWirelessEnabled -> context.getString(R.string.adb_widget_wireless_on)
            else -> context.getString(R.string.common_tap_to_enable)
        }
        val openIntent = PendingIntent.getActivity(
            context,
            4201,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_SCREEN, MainActivity.SCREEN_ADB),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val enableIntent = PendingIntent.getBroadcast(
            context,
            4202,
            Intent(context, AdbWidgetProvider::class.java).setAction(ACTION_ENABLE_ROOT_TCP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_adb).apply {
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_subtitle, subtitle)
                setTextViewText(
                    R.id.widget_action,
                    context.getString(if (state.rootTcpEnabled) R.string.common_enabled else R.string.common_enable),
                )
                setOnClickPendingIntent(R.id.widget_root, openIntent)
                setOnClickPendingIntent(R.id.widget_action, if (state.rootTcpEnabled) openIntent else enableIntent)
            }
            manager.updateAppWidget(id, views)
        }
    }

    companion object {
        private const val ACTION_ENABLE_ROOT_TCP = "com.arthur.roottools.action.WIDGET_ENABLE_ADB"

        fun requestUpdate(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, AdbWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val intent = Intent(appContext, AdbWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            appContext.sendBroadcast(intent)
        }
    }
}

