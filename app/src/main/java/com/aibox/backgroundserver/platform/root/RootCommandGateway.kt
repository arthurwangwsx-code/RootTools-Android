package com.aibox.backgroundserver.platform.root

import java.util.concurrent.TimeUnit

data class RootCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val ok: Boolean get() = exitCode == 0
}

class RootCommandGateway {
    fun execute(command: String, timeoutSeconds: Long = 5): RootCommandResult {
        return runCatching {
            val process = ProcessBuilder("su", "-c", command).start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return RootCommandResult(124, "", "root command timed out")
            }
            RootCommandResult(
                exitCode = process.exitValue(),
                stdout = process.inputStream.bufferedReader().use { it.readText() }.trim(),
                stderr = process.errorStream.bufferedReader().use { it.readText() }.trim(),
            )
        }.getOrElse {
            RootCommandResult(127, "", it.message ?: it.javaClass.simpleName)
        }
    }

    fun probe(): RootCommandResult = execute("id")
}
