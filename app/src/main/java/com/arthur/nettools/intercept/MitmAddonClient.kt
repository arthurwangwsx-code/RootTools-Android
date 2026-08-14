package com.arthur.nettools.intercept

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
    private var certWaiter: CompletableDeferred<String>? = null
    private var disconnectCallback: (() -> Unit)? = null

    private val incoming = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MitmAPI.MSG_GET_CA_CERTIFICATE -> certWaiter?.complete(msg.data.getString(MitmAPI.CERTIFICATE_RESULT).orEmpty())
                MitmAPI.MSG_ERROR -> certWaiter?.completeExceptionally(IllegalStateException("MITM add-on returned an error"))
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

    suspend fun connect(timeoutMs: Long = 10_000, onDisconnect: (() -> Unit)? = null): Boolean {
        disconnectCallback = onDisconnect
        if (bound && remote != null) return true
        connectWaiter = CompletableDeferred()
        val intent = Intent().setComponent(ComponentName(MitmAPI.PACKAGE_NAME, MitmAPI.MITM_SERVICE))
        val ok = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT) }.getOrDefault(false)
        if (!ok) return false
        return withTimeout(timeoutMs) { connectWaiter.await() }
    }

    suspend fun requestCertificate(timeoutMs: Long = 15_000): String {
        check(connect()) { "Cannot bind MITM add-on" }
        val waiter = CompletableDeferred<String>()
        certWaiter = waiter
        val msg = Message.obtain(null, MitmAPI.MSG_GET_CA_CERTIFICATE).apply { replyTo = incoming }
        remote?.send(msg) ?: error("MITM add-on is not connected")
        return try { withTimeout(timeoutMs) { waiter.await() } } finally { certWaiter = null }
    }

    fun startProxy(port: Int, fullPayload: Boolean, allowInsecureUpstream: Boolean): ParcelFileDescriptor {
        val service = remote ?: error("MITM add-on is not connected")
        val pair = ParcelFileDescriptor.createReliableSocketPair()
        val config = MitmAPI.MitmConfig().apply {
            proxyPort = port
            transparentMode = true
            sslInsecure = allowInsecureUpstream
            dumpMasterSecrets = false
            shortPayload = !fullPayload
            proxyAuth = null
            // Keep the stable v1.4 runtime on its default option set. Newer add-on builds
            // accept more --set options, but the release channel should remain backwards-compatible.
            additionalOptions = ""
        }
        val bundle = Bundle().apply { putSerializable(MitmAPI.MITM_CONFIG, config) }
        val msg = Message.obtain(null, MitmAPI.MSG_START_MITM, 0, 0, pair[0]).apply { setData(bundle) }
        service.send(msg)
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
}
