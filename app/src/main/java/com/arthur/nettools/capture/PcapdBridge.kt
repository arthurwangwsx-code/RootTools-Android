package com.arthur.nettools.capture

import android.content.Context
import java.io.File

/**
 * Root-side bridge around PCAPdroid's pcapd daemon.
 */
class PcapdBridge(private val context: Context) {
    private val pidFile = File(context.filesDir, "pcapd-bridge.pid")

    val binaryPath: String?
        get() = File(context.applicationInfo.nativeLibraryDir, "libpcapd.so")
            .takeIf { it.exists() && it.canRead() }
            ?.absolutePath

    private val bridgePath: String?
        get() = File(context.applicationInfo.nativeLibraryDir, "libnettools_pcap_bridge.so")
            .takeIf { it.exists() && it.canRead() }
            ?.absolutePath

    fun start(output: File, uid: Int?): Result<Unit> = runCatching {
        val packaged = binaryPath ?: error("pcapd binary is not packaged")
        val packagedBridge = bridgePath ?: error("root capture bridge is not packaged")
        val binary = "/data/local/tmp/nettools-pcapd"
        val bridge = "/data/local/tmp/nettools-pcap-bridge"
        output.parentFile?.mkdirs()
        val log = File(output.parentFile, output.nameWithoutExtension + ".root.log")
        val staged = RootShell.exec(
            "cp '$packaged' '$binary' && cp '$packagedBridge' '$bridge' && chmod 0755 '$binary' '$bridge'",
        )
        check(staged.code == 0) { "capture backend staging failed: ${staged.output}" }
        val command = "$bridge '$binary' '${output.absolutePath}' ${uid ?: -1} >'${log.absolutePath}' 2>&1 & echo \$!"
        val launched = RootShell.exec(command)
        val pid = launched.output.lineSequence().map { it.trim() }.firstOrNull { it.all(Char::isDigit) }
            ?: error("capture bridge launch failed: ${launched.output}")
        pidFile.writeText(pid)
        Thread.sleep(180)
        check(isRunning()) { "capture bridge exited early; inspect ${log.absolutePath}" }
    }

    fun stop(): Result<Unit> = runCatching {
        val pid = pidFile.takeIf(File::exists)?.readText()?.trim()?.toLongOrNull()
        if (pid != null) {
            RootShell.exec("kill -TERM $pid 2>/dev/null || true")
            repeat(20) {
                if (!processAlive(pid)) return@repeat
                Thread.sleep(80)
            }
            if (processAlive(pid)) RootShell.exec("kill -KILL $pid 2>/dev/null || true")
        }
        pidFile.delete()
    }

    fun isRunning(): Boolean {
        val pid = pidFile.takeIf(File::exists)?.readText()?.trim()?.toLongOrNull() ?: return false
        return processAlive(pid)
    }

    private fun processAlive(pid: Long): Boolean = RootShell.exec("kill -0 $pid 2>/dev/null").code == 0
}
