package com.arthur.nfclab.root

import android.content.Context
import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class RootNativeBridge(private val context: Context) {
    fun status(): String {
        if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" }) {
            return "Native Bridge unsupported ABI: ${Build.SUPPORTED_ABIS.joinToString()}"
        }

        val staged = stageAsset()
        val installed = "/data/local/tmp/nfc-tools-root-bridge-v2"
        val command = buildString {
            append("cp ").append(shellQuote(staged.absolutePath)).append(' ')
            append(shellQuote(installed)).append(" && ")
            append("chmod 0700 ").append(shellQuote(installed)).append(" && ")
            append(shellQuote(installed)).append(" status")
        }

        return runCatching {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }.trim()
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return@runCatching "Native Bridge timeout\n$output"
            }
            if (output.isBlank()) {
                "Native Bridge exited ${process.exitValue()} without output"
            } else {
                output
            }
        }.getOrElse {
            "Native Bridge failed: ${it.javaClass.simpleName}: ${it.message}"
        }
    }

    private fun stageAsset(): File {
        val dir = File(context.noBackupFilesDir, "rootbridge").apply { mkdirs() }
        val output = File(dir, "nfc-root-bridge")
        context.assets.open("rootbridge/arm64-v8a/nfc-root-bridge").use { input ->
            output.outputStream().use { target -> input.copyTo(target) }
        }
        output.setReadable(true, true)
        return output
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
