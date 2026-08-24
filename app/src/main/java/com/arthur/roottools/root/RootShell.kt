package com.arthur.roottools.root

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Root command executor backed by one process-wide `su` session.
 *
 * Magisk emits its superuser grant notification when a new `su` process is created. The old
 * implementation used `su -c` for every collector/action, so normal dashboard sampling could
 * trigger that notification repeatedly, including while a foreground policy service was alive.
 *
 * Every RootShell instance now shares the same serialized root session. Each command is executed
 * through the platform `timeout` utility, which creates a dedicated process group and terminates
 * the whole group on timeout. This prevents timed-out pipelines from becoming orphan root
 * processes while keeping privilege acquisition out of normal hot paths.
 */
class RootShell internal constructor(
    private val session: PersistentRootSession = SHARED_SESSION,
) {
    data class Result(
        val exitCode: Int,
        val output: String,
        val timedOut: Boolean = false,
    ) {
        val success: Boolean get() = !timedOut && exitCode == 0
    }

    suspend fun execute(command: String, timeoutSeconds: Long = 8): Result =
        session.execute(command, timeoutSeconds)

    suspend fun executeBatch(commands: List<String>, timeoutSeconds: Long = 8): Result =
        execute(commands.joinToString("\n"), timeoutSeconds)

    suspend fun isAvailable(timeoutSeconds: Long = 4): Boolean {
        val result = execute("id -u", timeoutSeconds = timeoutSeconds)
        return result.success && result.output.trim() == "0"
    }

    private companion object {
        val SHARED_SESSION = PersistentRootSession(listOf("su"))
    }
}

/**
 * Serialized interactive shell transport. Internal so host JVM tests can exercise the protocol
 * with `/bin/sh` without requiring root.
 */
internal class PersistentRootSession(
    private val processCommand: List<String>,
) {
    private val mutex = Mutex()
    private val sequence = AtomicLong(0)

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var outputQueue: LinkedBlockingQueue<SessionEvent>? = null
    private var readerThread: Thread? = null

    internal var processLaunchCount: Int = 0
        private set

    suspend fun execute(command: String, timeoutSeconds: Long): RootShell.Result = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                executeLocked(command, timeoutSeconds)
            } catch (cancelled: CancellationException) {
                invalidateSession()
                throw cancelled
            } catch (error: Throwable) {
                invalidateSession()
                RootShell.Result(-1, error.message ?: error.javaClass.simpleName)
            }
        }
    }

    private suspend fun executeLocked(command: String, timeoutSeconds: Long): RootShell.Result {
        ensureSession()
        val activeWriter = writer ?: error("Root shell stdin unavailable")
        val activeQueue = outputQueue ?: error("Root shell output queue unavailable")
        val marker = "__ROOTTOOLS_END_${sequence.incrementAndGet()}_${System.nanoTime()}__"
        val effectiveTimeoutSeconds = timeoutSeconds.coerceAtLeast(1)

        // Android's toybox timeout (API 30+ target) creates a separate process group unless
        // --foreground is requested. Running the actual payload under `sh -c` preserves isolation
        // for exit/cd/local variables, and -k guarantees a TERM-ignoring descendant cannot survive.
        activeWriter.write(
            "timeout -k ${TIMEOUT_KILL_GRACE_SECONDS}s ${effectiveTimeoutSeconds}s sh -c ${shellQuote(command)}\n",
        )
        activeWriter.write("__roottools_code=\$?\n")
        activeWriter.write("printf '\\n${marker}:%s\\n' \"\$__roottools_code\"\n")
        activeWriter.flush()

        val outputLines = mutableListOf<String>()
        val deadline = System.nanoTime() +
            TimeUnit.SECONDS.toNanos(effectiveTimeoutSeconds + TRANSPORT_GRACE_SECONDS)
        while (true) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0L) {
                invalidateSession()
                return RootShell.Result(-1, "Command timed out", timedOut = true)
            }
            val event = runInterruptible(Dispatchers.IO) {
                activeQueue.poll(remaining, TimeUnit.NANOSECONDS)
            }
            when (event) {
                null -> {
                    // We never retry the same command automatically because a write action may
                    // have partially run before the timeout.
                    invalidateSession()
                    return RootShell.Result(-1, "Command timed out", timedOut = true)
                }
                is SessionEvent.Line -> {
                    val line = event.value
                    if (line.startsWith("$marker:")) {
                        val exitCode = line.substringAfter(':').trim().toIntOrNull()
                            ?: error("Root shell returned an invalid exit code")
                        return RootShell.Result(
                            exitCode = exitCode,
                            output = outputLines.joinToString("\n"),
                            timedOut = exitCode == TIMEOUT_EXIT_CODE || exitCode == TIMEOUT_KILLED_EXIT_CODE,
                        )
                    }
                    outputLines += line
                }
                is SessionEvent.Closed -> error(event.reason)
            }
        }
    }

    private fun ensureSession() {
        val current = process
        if (current?.isAlive == true && writer != null && reader != null && outputQueue != null && readerThread?.isAlive == true) return

        invalidateSession()
        val created = ProcessBuilder(processCommand)
            .redirectErrorStream(true)
            .start()
        val createdReader = created.inputStream.bufferedReader()
        val createdQueue = LinkedBlockingQueue<SessionEvent>()
        val createdReaderThread = Thread({
            try {
                while (true) {
                    val line = createdReader.readLine() ?: break
                    createdQueue.offer(SessionEvent.Line(line))
                }
                createdQueue.offer(SessionEvent.Closed("Root shell closed before command completed"))
            } catch (error: Throwable) {
                createdQueue.offer(SessionEvent.Closed(error.message ?: "Root shell reader stopped"))
            }
        }, "RootTools-su-reader").apply {
            isDaemon = true
            start()
        }
        process = created
        writer = created.outputStream.bufferedWriter()
        reader = createdReader
        outputQueue = createdQueue
        readerThread = createdReaderThread
        processLaunchCount += 1
    }

    private fun invalidateSession() {
        runCatching { writer?.close() }
        runCatching { reader?.close() }
        runCatching { process?.destroyForcibly() }
        runCatching { readerThread?.interrupt() }
        outputQueue?.clear()
        writer = null
        reader = null
        outputQueue = null
        readerThread = null
        process = null
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private sealed interface SessionEvent {
        data class Line(val value: String) : SessionEvent
        data class Closed(val reason: String) : SessionEvent
    }

    private companion object {
        const val TIMEOUT_EXIT_CODE = 124
        const val TIMEOUT_KILLED_EXIT_CODE = 137
        const val TIMEOUT_KILL_GRACE_SECONDS = "0.2"
        const val TRANSPORT_GRACE_SECONDS = 2L
    }
}
