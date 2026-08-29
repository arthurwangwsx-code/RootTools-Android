package com.aibox.backgroundserver.engine.wireguard

import android.app.Application
import android.content.Context
import com.aibox.backgroundserver.platform.root.RootCommandGateway

object WireGuardRuntime {
    @Volatile
    private var instance: WireGuardServerManager? = null

    fun get(context: Context): WireGuardServerManager =
        instance ?: synchronized(this) {
            instance ?: WireGuardServerManager(
                application = context.applicationContext as Application,
                root = RootCommandGateway(),
            ).also { instance = it }
        }
}
