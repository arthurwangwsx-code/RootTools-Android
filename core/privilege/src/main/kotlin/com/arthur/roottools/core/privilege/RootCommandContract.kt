package com.arthur.roottools.core.privilege

data class RootCommandResult(
    val exitCode: Int,
    val output: String,
    val errorOutput: String = "",
    val timedOut: Boolean = false,
) {
    val success: Boolean get() = !timedOut && exitCode == 0

    // Compatibility names for companion code while the shared contract becomes canonical.
    val ok: Boolean get() = success
    val stdout: String get() = output
    val stderr: String get() = errorOutput
}

object PosixShell {
    fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}

object RootExecutionPolicy {
    const val DEFAULT_TIMEOUT_SECONDS = 8L
    const val MAX_TIMEOUT_SECONDS = 120L

    fun validatedTimeoutSeconds(value: Long): Long? = value.takeIf { it in 1..MAX_TIMEOUT_SECONDS }

    fun isolatedSuCommand(script: String, timeoutSeconds: Long): List<String>? {
        val timeout = validatedTimeoutSeconds(timeoutSeconds) ?: return null
        if (script.isBlank() || script.indexOf('\u0000') >= 0) return null
        val payload = "timeout -k 0.2s ${timeout}s sh -c ${PosixShell.quote(script)}"
        return listOf("su", "-c", payload)
    }
}
