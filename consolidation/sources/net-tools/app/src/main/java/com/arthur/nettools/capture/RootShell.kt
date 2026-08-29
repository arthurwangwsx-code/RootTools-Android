package com.arthur.nettools.capture

import java.io.BufferedReader
import java.io.InputStreamReader

data class ShellResult(val code: Int, val output: String)

object RootShell {
    fun exec(command: String): ShellResult {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        return ShellResult(process.waitFor(), output.trim())
    }

    fun hasRoot(): Boolean = exec("id").output.contains("uid=0")

    fun commandPath(name: String): String? {
        val result = exec("command -v $name")
        return result.output.takeIf { result.code == 0 && it.startsWith("/") }
    }
}
