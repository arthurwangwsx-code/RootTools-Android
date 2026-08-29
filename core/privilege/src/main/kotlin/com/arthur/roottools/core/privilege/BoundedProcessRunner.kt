package com.arthur.roottools.core.privilege

import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class BoundedProcessRunner(
    private val outputLimitBytes: Int = DEFAULT_OUTPUT_LIMIT_BYTES,
) {
    init {
        require(outputLimitBytes in 1..MAX_OUTPUT_LIMIT_BYTES)
    }

    fun run(command: List<String>, timeoutMillis: Long): RootCommandResult {
        if (command.isEmpty() || command.any { it.isEmpty() || it.indexOf('\u0000') >= 0 }) {
            return RootCommandResult(INVALID_INPUT_EXIT_CODE, "", "invalid process command")
        }
        if (timeoutMillis !in 1..MAX_WAIT_MILLIS) {
            return RootCommandResult(INVALID_INPUT_EXIT_CODE, "", "invalid process timeout")
        }
        return runCatching {
            val process = ProcessBuilder(command).start()
            val stdout = BoundedStreamCollector(process.inputStream, outputLimitBytes, "roottools-stdout")
            val stderr = BoundedStreamCollector(process.errorStream, outputLimitBytes, "roottools-stderr")
            stdout.start()
            stderr.start()
            val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finished) process.destroyForcibly()
            stdout.await()
            stderr.await()
            if (!finished) {
                RootCommandResult(
                    exitCode = TIMEOUT_EXIT_CODE,
                    output = stdout.text(),
                    errorOutput = stderr.text().ifBlank { "root command timed out" },
                    timedOut = true,
                )
            } else {
                val exitCode = process.exitValue()
                RootCommandResult(
                    exitCode = exitCode,
                    output = stdout.text(),
                    errorOutput = stderr.text(),
                    timedOut = exitCode == TIMEOUT_EXIT_CODE || exitCode == TIMEOUT_KILLED_EXIT_CODE,
                )
            }
        }.getOrElse { error ->
            RootCommandResult(
                exitCode = PROCESS_START_EXIT_CODE,
                output = "",
                errorOutput = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private class BoundedStreamCollector(
        private val stream: InputStream,
        private val limit: Int,
        name: String,
    ) {
        private val bytes = ArrayList<Byte>(minOf(limit, 8 * 1024))
        private val worker = thread(start = false, isDaemon = true, name = name) {
            stream.use { input ->
                val buffer = ByteArray(4 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    val remaining = limit - bytes.size
                    val retained = minOf(read, remaining.coerceAtLeast(0))
                    repeat(retained) { index -> bytes += buffer[index] }
                }
            }
        }

        fun start() = worker.start()

        fun await() {
            worker.join(STREAM_JOIN_MILLIS)
        }

        fun text(): String = bytes.toByteArray().toString(Charsets.UTF_8).trim()
    }

    private companion object {
        const val DEFAULT_OUTPUT_LIMIT_BYTES = 256 * 1024
        const val MAX_OUTPUT_LIMIT_BYTES = 1024 * 1024
        const val MAX_WAIT_MILLIS = 122_000L
        const val STREAM_JOIN_MILLIS = 2_000L
        const val INVALID_INPUT_EXIT_CODE = 2
        const val PROCESS_START_EXIT_CODE = 127
        const val TIMEOUT_EXIT_CODE = 124
        const val TIMEOUT_KILLED_EXIT_CODE = 137
    }
}
