package com.arthur.roottools.privilege

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

class ShizukuUserServiceClient(context: Context) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val args = Shizuku.UserServiceArgs(ComponentName(appContext, PrivilegeUserService::class.java))
        .processNameSuffix("privilege")
        .tag("roottools-privilege-v1")
        .version(1)
        .daemon(false)
        .debuggable(false)

    @Volatile private var service: IPrivilegeUserService? = null
    @Volatile private var pending = CompletableDeferred<IPrivilegeUserService>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val resolved = IPrivilegeUserService.Stub.asInterface(binder) ?: return
            service = resolved
            if (!pending.isCompleted) pending.complete(resolved)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            pending = CompletableDeferred()
        }
    }

    suspend fun <T> call(block: (IPrivilegeUserService) -> T): Result<T> = runCatching {
        val remote = ensureConnected() ?: error("Shizuku UserService unavailable")
        withContext(Dispatchers.IO) { block(remote) }
    }.onFailure {
        if (it is android.os.DeadObjectException) resetConnection()
    }

    suspend fun selfTest(): Result<String> = call { it.frameworkSelfTest(appContext.packageName) }

    fun close() {
        runCatching { Shizuku.unbindUserService(args, connection, true) }
        service = null
    }

    private suspend fun ensureConnected(): IPrivilegeUserService? = mutex.withLock {
        service?.let { return it }
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        if (pending.isCompleted) pending = CompletableDeferred()
        withContext(Dispatchers.Main.immediate) {
            Shizuku.bindUserService(args, connection)
        }
        withTimeoutOrNull(CONNECT_TIMEOUT_MS) { pending.await() }
    }

    private fun resetConnection() {
        service = null
        pending = CompletableDeferred()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000L
    }
}
