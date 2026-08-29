package com.aibox.backgroundserver.platform.power

import android.content.Context
import android.os.PowerManager
import com.aibox.backgroundserver.platform.root.RootCommandGateway

class PowerController(
    context: Context,
    private val root: RootCommandGateway,
) {
    private val powerManager = context.getSystemService(PowerManager::class.java)

    fun isInteractive(): Boolean = powerManager.isInteractive

    fun sleepDisplay() = root.execute("input keyevent 223")

    fun wakeDisplay() = root.execute("input keyevent 224")

    fun readDoubleTapToWake(): Boolean? {
        val result = root.execute("settings get secure double_tap_to_wake")
        if (!result.ok) return null
        return when (result.stdout.trim()) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    fun setDoubleTapToWake(enabled: Boolean) =
        root.execute("settings put secure double_tap_to_wake ${if (enabled) 1 else 0}")
}
