package com.arthur.roottools.feature.network.inspection.intercept

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import com.pcapdroid.mitm.MitmAPI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

class MitmAddonClient(private val context: Context) {
    private var remote: Messenger? = null
    private var bound = false
    private var connectWaiter = CompletableDeferred<Boolean>()
    private var certificateWaiter: CompletableDeferred<String>? = null
    private var disconnectCallback: (() -> Unit)? = null

    private val incoming = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            when (message.what) {
                MitmAPI.MSG_GET_CA_CERTIFICATE -> certificateWaiter?.complete(
                    message.data.getString(MitmAPI.CERTIFICATE_RESULT).orEmpty(),
                )
                MitmAPI.MSG_ERROR -> certificateWaiter?.completeExceptionally(
                    IllegalStateException("MITM add-on returned an error"),
                )
            }
        }
    })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = Messenger(service)
            bound = true
            if (!connectWaiter.isCompleted) connectWaiter.complete(true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            bound = false
            disconnectCallback?.invoke()
        }

        override fun onBindingDied(name: ComponentName?) = onServiceDisconnected(name)

        override fun onNullBinding(name: ComponentName?) {
            if (!connectWaiter.isCompleted) connectWaiter.complete(false)
        }
    }

    suspend fun connect(
        timeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
        onDisconnect: (() -> Unit)? = null,
    ): Boolean {
        disconnectCallback = onDisconnect
        if (bound && remote != null) return true
        connectWaiter = CompletableDeferred()
        val intent = Intent().setComponent(ComponentName(MitmAPI.PACKAGE_NAME, MitmAPI.MITM_SERVICE))
        val started = runCatching {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
        }.getOrDefault(false)
        if (!started) return false
        return withTimeout(timeoutMs) { connectWaiter.await() }
    }

    suspend fun requestCertificate(timeoutMs: Long = DEFAULT_CERTIFICATE_TIMEOUT_MS): String {
        check(connect()) { "Cannot bind MITM add-on" }
        val waiter = CompletableDeferred<String>()
        certificateWaiter = waiter
        val message = Message.obtain(null, MitmAPI.MSG_GET_CA_CERTIFICATE).apply { replyTo = incoming }
        remote?.send(message) ?: error("MITM add-on is not connected")
        return try {
            withTimeout(timeoutMs) { waiter.await() }
        } finally {
            certificateWaiter = null
        }
    }

    fun startProxy(
        port: Int,
        fullPayload: Boolean,
        allowInsecureUpstream: Boolean,
    ): ParcelFileDescriptor {
        require(port in 1024..65535) { "Proxy port is outside the supported range" }
        val service = remote ?: error("MITM add-on is not connected")
        val pair = ParcelFileDescriptor.createReliableSocketPair()
        val config = MitmAPI.MitmConfig().apply {
            proxyPort = port
            transparentMode = true
            sslInsecure = allowInsecureUpstream
            dumpMasterSecrets = false
            shortPayload = !fullPayload
            proxyAuth = null
            additionalOptions = ""
        }
        val bundle = Bundle().apply { putSerializable(MitmAPI.MITM_CONFIG, config) }
        val message = Message.obtain(null, MitmAPI.MSG_START_MITM, 0, 0, pair[0]).apply { data = bundle }
        service.send(message)
        pair[0].close()
        return pair[1]
    }

    fun stopProxy() {
        runCatching { remote?.send(Message.obtain(null, MitmAPI.MSG_STOP_MITM)) }
    }

    fun disableDoze() {
        runCatching { remote?.send(Message.obtain(null, MitmAPI.MSG_DISABLE_DOZE)) }
    }

    fun disconnect() {
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
        remote = null
    }

    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000L
        private const val DEFAULT_CERTIFICATE_TIMEOUT_MS = 15_000L
    }
}
