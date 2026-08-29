package com.arthur.roottools.feature.network.inspection.data

import android.content.Context
import com.arthur.roottools.feature.network.inspection.capture.CaptureSignal
import com.arthur.roottools.feature.network.inspection.capture.NetworkCaptureCommandPolicy
import com.arthur.roottools.root.RootShell
import kotlinx.coroutines.delay
import java.io.File

/**
 * Root-side bridge around PCAPdroid's pcapd daemon.
 */
class PcapdBridge(
    private val context: Context,
    private val shell: RootShell,
) {
    private val pidFile = File(context.filesDir, "pcapd-bridge.pid")

    val binaryPath: String?
        get() = File(context.applicationInfo.nativeLibraryDir, "libpcapd.so")
            .takeIf { it.exists() && it.canRead() }
            ?.absolutePath

    private val bridgePath: String?
        get() = File(context.applicationInfo.nativeLibraryDir, "libroottools_pcap_bridge.so")
            .takeIf { it.exists() && it.canRead() }
            ?.absolutePath

    suspend fun start(output: File, uid: Int?): Result<Unit> = runCatching {
        val packaged = binaryPath ?: error("pcapd binary is not packaged")
        val packagedBridge = bridgePath ?: error("root capture bridge is not packaged")
        val binary = "/data/local/tmp/roottools-pcapd"
        val bridge = "/data/local/tmp/roottools-pcap-bridge"
        output.parentFile?.mkdirs()
        val log = File(output.parentFile, output.nameWithoutExtension + ".root.log")
        val commands = NetworkCaptureCommandPolicy.pcapdLaunch(
            packagedPcapd = packaged,
            packagedBridge = packagedBridge,
            stagedPcapd = binary,
            stagedBridge = bridge,
            outputPcap = output.absolutePath,
            outputLog = log.absolutePath,
            uid = uid,
        ) ?: error("capture backend paths or UID failed validation")
        val staged = shell.execute(commands.stage)
        check(staged.success) { "capture backend staging failed: ${staged.output}" }
        val launched = shell.execute(commands.launch)
        check(launched.success) { "capture bridge launch failed: ${launched.output}" }
        val pid = launched.output.lineSequence().map { it.trim() }.firstOrNull { it.all(Char::isDigit) }
            ?: error("capture bridge launch failed: ${launched.output}")
        pidFile.writeText(pid)
        Thread.sleep(180)
        check(isRunning()) { "capture bridge exited early; inspect ${log.absolutePath}" }
    }

    suspend fun stop(): Result<Unit> = runCatching {
        val pid = pidFile.takeIf(File::exists)?.readText()?.trim()?.toLongOrNull()
        if (pid != null) {
            signal(pid, CaptureSignal.TERMINATE)
            for (attempt in 0 until 20) {
                if (!processAlive(pid)) break
                delay(80)
            }
            if (processAlive(pid)) signal(pid, CaptureSignal.KILL)
        }
        pidFile.delete()
    }

    suspend fun isRunning(): Boolean {
        val pid = pidFile.takeIf(File::exists)?.readText()?.trim()?.toLongOrNull() ?: return false
        return processAlive(pid)
    }

    private suspend fun processAlive(pid: Long): Boolean {
        val command = NetworkCaptureCommandPolicy.processAlive(pid) ?: return false
        return shell.execute(command, timeoutSeconds = 3).success
    }

    private suspend fun signal(pid: Long, signal: CaptureSignal) {
        val command = NetworkCaptureCommandPolicy.signal(pid, signal) ?: return
        shell.execute(command, timeoutSeconds = 3)
    }
}
