package com.arthur.roottools.feature.network.inspection.capture

enum class CaptureBinary(val commandName: String) {
    TCPDUMP("tcpdump"),
}

enum class CaptureSignal(val number: Int) {
    INTERRUPT(2),
    TERMINATE(15),
    KILL(9),
}

data class PcapdLaunchCommands(
    val stage: String,
    val launch: String,
)

object NetworkCaptureCommandPolicy {
    fun commandPath(binary: CaptureBinary): String = "command -v ${binary.commandName}"

    fun pcapdLaunch(
        packagedPcapd: String,
        packagedBridge: String,
        stagedPcapd: String,
        stagedBridge: String,
        outputPcap: String,
        outputLog: String,
        uid: Int?,
    ): PcapdLaunchCommands? {
        val paths = listOf(
            packagedPcapd,
            packagedBridge,
            stagedPcapd,
            stagedBridge,
            outputPcap,
            outputLog,
        ).map(::safeAbsolutePath)
        if (paths.any { it == null }) return null
        val safeUid = uid ?: -1
        if (safeUid != -1 && safeUid < FIRST_APPLICATION_UID) return null
        val sourcePcapd = requireNotNull(paths[0])
        val sourceBridge = requireNotNull(paths[1])
        val targetPcapd = requireNotNull(paths[2])
        val targetBridge = requireNotNull(paths[3])
        val pcap = requireNotNull(paths[4])
        val log = requireNotNull(paths[5])
        return PcapdLaunchCommands(
            stage = "cp ${shellQuote(sourcePcapd)} ${shellQuote(targetPcapd)} && " +
                "cp ${shellQuote(sourceBridge)} ${shellQuote(targetBridge)} && " +
                "chmod 0755 ${shellQuote(targetPcapd)} ${shellQuote(targetBridge)}",
            launch = "${shellQuote(targetBridge)} ${shellQuote(targetPcapd)} ${shellQuote(pcap)} $safeUid " +
                ">${shellQuote(log)} 2>&1 & echo \$!",
        )
    }

    fun tcpdumpLaunch(
        binaryPath: String,
        outputPcap: String,
        outputLog: String,
        pidFile: String,
    ): String? {
        val binary = safeAbsolutePath(binaryPath) ?: return null
        val pcap = safeAbsolutePath(outputPcap) ?: return null
        val log = safeAbsolutePath(outputLog) ?: return null
        val pid = safeAbsolutePath(pidFile) ?: return null
        return "${shellQuote(binary)} -i any -s 0 -U -w ${shellQuote(pcap)} " +
            ">${shellQuote(log)} 2>&1 & echo \$! > ${shellQuote(pid)}"
    }

    fun processAlive(pid: Long): String? = validPid(pid)?.let { "kill -0 $it 2>/dev/null" }

    fun signal(pid: Long, signal: CaptureSignal): String? =
        validPid(pid)?.let { "kill -${signal.number} $it 2>/dev/null" }

    private fun validPid(pid: Long): Long? = pid.takeIf { it in 2..Int.MAX_VALUE.toLong() }

    private fun safeAbsolutePath(value: String): String? = value.takeIf {
        it.startsWith('/') && '\u0000' !in it && '\n' !in it && '\r' !in it
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private const val FIRST_APPLICATION_UID = 10_000
}
