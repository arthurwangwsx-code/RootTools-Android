package com.arthur.roottools.root

import com.arthur.roottools.core.privilege.BoundedProcessRunner
import com.arthur.roottools.core.privilege.RootCommandResult
import com.arthur.roottools.core.privilege.RootExecutionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Root command executor backed by the shared privilege Core contract.
 *
 * Every RootShell instance shares one serialized executor. Commands use isolated `su -c` processes
 * because some Magisk/device combinations close long-lived interactive `su` stdin immediately.
 * The shared Core transport applies quoting, timeout process-group isolation, concurrent stream
 * draining, and output bounds consistently with companion apps.
 */
class RootShell internal constructor(
    private val executor: SerializedRootExecutor = SHARED_EXECUTOR,
) {
    suspend fun execute(command: String, timeoutSeconds: Long = 8): RootCommandResult =
        executor.execute(command, timeoutSeconds)

    suspend fun executeBatch(commands: List<String>, timeoutSeconds: Long = 8): RootCommandResult =
        execute(commands.joinToString("\n"), timeoutSeconds)

    suspend fun isAvailable(timeoutSeconds: Long = 4): Boolean {
        val result = execute("id -u", timeoutSeconds = timeoutSeconds)
        return result.success && result.output.trim() == "0"
    }

    private companion object {
        val SHARED_EXECUTOR = SerializedRootExecutor()
    }
}

internal class SerializedRootExecutor(
    private val runProcess: (List<String>, Long) -> RootCommandResult = BoundedProcessRunner()::run,
) {
    private val mutex = Mutex()

    suspend fun execute(command: String, timeoutSeconds: Long): RootCommandResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val transport = RootExecutionPolicy.isolatedSuCommand(command, timeoutSeconds)
                ?: return@withLock RootCommandResult(2, "", "invalid root command input")
            runProcess(transport, (timeoutSeconds + TRANSPORT_GRACE_SECONDS) * 1_000)
        }
    }

    private companion object {
        const val TRANSPORT_GRACE_SECONDS = 2L
    }
}
