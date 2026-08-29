package com.arthur.nfclab.platform.xiaomi

import android.content.Context
import android.util.Log
import com.arthur.nfclab.domain.AccessDiagnosticConclusion
import com.arthur.nfclab.domain.AccessDiagnosticReport
import com.arthur.nfclab.domain.AccessDiagnosticSignals
import com.arthur.nfclab.domain.AccessReaderOutcome
import com.arthur.nfclab.domain.NfcCapability
import com.arthur.nfclab.domain.NfcCard
import com.arthur.nfclab.domain.NfcDeviceProfile
import com.arthur.nfclab.platform.access.AccessDiagnosticProbe
import java.io.BufferedReader
import java.io.InterruptedIOException
import java.io.InputStreamReader
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Read-only Xiaomi RF/eSE compatibility probe.
 *
 * It observes Xiaomi's NfcReaderDetector/NfcService logs and the sanitized reader summary from
 * dumpsys. Raw AIDs/NFCEE payloads are never persisted in the report.
 */
class XiaomiAccessDiagnosticProbe(
    private val context: Context,
) : AccessDiagnosticProbe {
    override val id: String = PROVIDER_ID
    override val priority: Int = 100

    private var capture: Capture? = null

    override fun supports(profile: NfcDeviceProfile): Boolean {
        val manufacturer = profile.identity.manufacturer.lowercase()
        val xiaomiFamily = manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi") ||
            manufacturer.contains("poco")
        return xiaomiFamily &&
            profile.rootAvailable &&
            profile.has(NfcCapability.MIFARE_OFF_HOST) &&
            profile.primaryEse?.let { it.connected ?: it.available } == true
    }

    override fun start(profile: NfcDeviceProfile, activeCard: NfcCard?): Result<Unit> = runCatching {
        check(capture == null) { "已有 Xiaomi 门禁诊断正在运行" }
        require(supports(profile)) { "当前设备不满足 Xiaomi eSE/M1 兼容性诊断条件" }

        val startedAtMs = System.currentTimeMillis()
        val baseline = readReaderSnapshot()
        val lines = Collections.synchronizedList(mutableListOf<String>())
        val process = ProcessBuilder(
            "su",
            "-M",
            "-c",
            "exec logcat -b main -v epoch -T 1 NfcRDDT:V NfcService:V '*:S'",
        )
            .redirectErrorStream(true)
            .start()

        val readerThread = Thread({
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (!Thread.currentThread().isInterrupted) {
                        val line = reader.readLine() ?: break
                        if (!belongsToSession(line, startedAtMs)) continue
                        synchronized(lines) {
                            if (lines.size < MAX_CAPTURE_LINES) lines += line
                        }
                    }
                }
            } catch (error: Exception) {
                // Closing/destroying logcat interrupts a blocking read. That is the normal stop
                // path and must never crash the application process.
                if (error !is InterruptedIOException && process.isAlive && !Thread.currentThread().isInterrupted) {
                    Log.w(TAG, "diagnostic log stream ended unexpectedly", error)
                }
            }
        }, "xiaomi-access-diag-logcat").apply {
            isDaemon = true
            start()
        }

        capture = Capture(
            sessionId = "access-$startedAtMs",
            startedAtMs = startedAtMs,
            cardTitle = activeCard?.title,
            cardSourceLabel = activeCard?.sourceLabel,
            activeCardId = activeCard?.id,
            baselineReaderDump = baseline,
            logProcess = process,
            logReaderThread = readerThread,
            logLines = lines,
        )
    }

    override fun stop(outcome: AccessReaderOutcome): Result<AccessDiagnosticReport> {
        val current = capture ?: return Result.failure(IllegalStateException("当前没有 Xiaomi 门禁诊断"))
        return runCatching {
            // Xiaomi's reader detector finalizes a session shortly after Field Off. Give it enough
            // time to emit protocol/NFCEE evidence before the log stream is closed.
            Thread.sleep(SESSION_SETTLE_MS)
            stopLogcat(current)
            val finalReaderDump = readReaderSnapshot()
            val logs = synchronized(current.logLines) { current.logLines.toList() }
            XiaomiAccessDiagnosticAnalyzer.analyze(
                sessionId = current.sessionId,
                startedAtMs = current.startedAtMs,
                finishedAtMs = System.currentTimeMillis(),
                cardTitle = current.cardTitle,
                cardSourceLabel = current.cardSourceLabel,
                activeCardId = current.activeCardId,
                outcome = outcome,
                baselineReaderDump = current.baselineReaderDump,
                finalReaderDump = finalReaderDump,
                logLines = logs,
            )
        }.also {
            capture = null
        }
    }

    override fun cancel() {
        capture?.let(::stopLogcat)
        capture = null
    }

    private fun stopLogcat(capture: Capture) {
        capture.logProcess.destroy()
        if (!capture.logProcess.waitFor(600, TimeUnit.MILLISECONDS)) {
            capture.logProcess.destroyForcibly()
        }
        capture.logReaderThread.interrupt()
        runCatching { capture.logReaderThread.join(500) }
    }

    private fun readReaderSnapshot(): String {
        val command = "dumpsys nfc 2>/dev/null | grep -E '^(Counts:|Readers:|ID:)' | head -n 260"
        val process = ProcessBuilder("su", "-M", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(4, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return ""
        }
        return output
    }

    private fun belongsToSession(line: String, startedAtMs: Long): Boolean {
        val epochSeconds = line.substringBefore(' ').toDoubleOrNull() ?: return false
        return (epochSeconds * 1_000.0).toLong() >= startedAtMs - LOG_START_TOLERANCE_MS
    }

    private data class Capture(
        val sessionId: String,
        val startedAtMs: Long,
        val cardTitle: String?,
        val cardSourceLabel: String?,
        val activeCardId: String?,
        val baselineReaderDump: String,
        val logProcess: Process,
        val logReaderThread: Thread,
        val logLines: MutableList<String>,
    )

    companion object {
        const val PROVIDER_ID = "xiaomi.reader-detector"
        private const val TAG = "AccessDiag"
        private const val MAX_CAPTURE_LINES = 2000
        private const val SESSION_SETTLE_MS = 1400L
        private const val LOG_START_TOLERANCE_MS = 250L
    }
}

internal object XiaomiAccessDiagnosticAnalyzer {
    private val fieldSessionRegex = Regex("Here we go!")
    private val nfceeActionRegex = Regex("onNfceeActionNotification\\s+nfceeId\\s*=\\s*192")
    private val highestRegex = Regex("Hightest:(\\d+)")
    private val readerIdRegex = Regex("\\bID:(\\d+)")
    private val readerTimeRegex = Regex("\\bTM:(\\d+)")
    private val readerHighestRegex = Regex("\\bHDL:(\\d+)")

    fun analyze(
        sessionId: String,
        startedAtMs: Long,
        finishedAtMs: Long,
        cardTitle: String?,
        cardSourceLabel: String?,
        activeCardId: String?,
        outcome: AccessReaderOutcome,
        baselineReaderDump: String,
        finalReaderDump: String,
        logLines: List<String>,
    ): AccessDiagnosticReport {
        val baselineReaders = parseReaderMap(baselineReaderDump)
        val finalReaders = parseReaderMap(finalReaderDump)
        val changedReaders = finalReaders.filter { (id, line) -> baselineReaders[id] != line }.values.toMutableList()
        finalReaders.values
            .filter { line -> (readerTimeRegex.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L) >= startedAtMs - 2_000L }
            .forEach { line -> if (line !in changedReaders) changedReaders += line }

        val fieldSessionCount = logLines.count { fieldSessionRegex.containsMatchIn(it) }
        val nfceeActionCount = logLines.count { nfceeActionRegex.containsMatchIn(it) }
        val cardActivationCount = logLines.count {
            it.contains("card has been activated:", ignoreCase = true) ||
                it.contains("activate card:", ignoreCase = true)
        }

        val highestFromLogs = logLines.mapNotNull { line ->
            highestRegex.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }.maxOrNull() ?: 0
        val highestFromReaders = changedReaders.mapNotNull { line ->
            readerHighestRegex.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }.maxOrNull() ?: 0
        val highestLayer = maxOf(highestFromLogs, highestFromReaders)

        val technologies = buildSet {
            changedReaders.forEach { line ->
                if (line.contains("TA:true")) add("A")
                if (line.contains("TB:true")) add("B")
                if (line.contains("TF:true")) add("F")
            }
        }
        val nfceeAidObserved = changedReaders.any { hasNonEmptyList(it, "NAids") }
        val hciAidObserved = changedReaders.any { hasNonEmptyList(it, "HAids") }
        val aidBearingEvidence = nfceeAidObserved || hciAidObserved || cardActivationCount > 0
        val activeCardMatched = when {
            activeCardId.isNullOrBlank() || !aidBearingEvidence -> null
            else -> {
                val needle = activeCardId.uppercase()
                logLines.any { it.uppercase().contains(needle) } ||
                    changedReaders.any { it.uppercase().contains(needle) }
            }
        }

        val readerFingerprintObserved = changedReaders.isNotEmpty()
        val rfFieldSeen = fieldSessionCount > 0 || readerFingerprintObserved || nfceeActionCount > 0
        val signals = AccessDiagnosticSignals(
            rfFieldSeen = rfFieldSeen,
            fieldSessionCount = fieldSessionCount,
            nfceeActionCount = nfceeActionCount,
            cardActivationCount = cardActivationCount,
            highestProtocolLayer = highestLayer,
            detectedTechnologies = technologies,
            nfceeAidObserved = nfceeAidObserved,
            hciAidObserved = hciAidObserved,
            activeCardMatched = activeCardMatched,
            readerFingerprintObserved = readerFingerprintObserved,
        )

        val conclusion = when {
            outcome == AccessReaderOutcome.OPENED -> AccessDiagnosticConclusion.SUCCESS
            !signals.rfFieldSeen -> AccessDiagnosticConclusion.NO_RF_FIELD
            !signals.cardInteractionSeen -> AccessDiagnosticConclusion.RF_FIELD_NO_CARD_INTERACTION
            outcome == AccessReaderOutcome.REACTED_BUT_FAILED -> AccessDiagnosticConclusion.CARD_INTERACTION_AUTH_FAILED
            outcome == AccessReaderOutcome.NO_REACTION -> AccessDiagnosticConclusion.CARD_INTERACTION_NO_READER_FEEDBACK
            else -> AccessDiagnosticConclusion.INCONCLUSIVE
        }

        val summary = when (conclusion) {
            AccessDiagnosticConclusion.SUCCESS -> "读卡器与手机模拟卡链路完整工作，可作为兼容基线。"
            AccessDiagnosticConclusion.NO_RF_FIELD -> "没有观察到 13.56 MHz RF Field。若贴卡位置正确，优先怀疑该门禁使用 125 kHz/其他非 NFC 频段，或读卡器没有向手机天线区域发场。"
            AccessDiagnosticConclusion.RF_FIELD_NO_CARD_INTERACTION -> "已经观察到 13.56 MHz RF Field，但没有进入有效的 eSE/卡片交互。更像是协议、调制、天线耦合或读卡器兼容性问题。"
            AccessDiagnosticConclusion.CARD_INTERACTION_AUTH_FAILED -> "手机已经与读卡器进入卡片/eSE 交互，但门禁最终拒绝。优先怀疑扇区认证、卡内数据或防复制策略，而不是手机 NFC 硬件。"
            AccessDiagnosticConclusion.CARD_INTERACTION_NO_READER_FEEDBACK -> "底层已经观察到卡片/eSE 交互，但门禁表面没有反馈。读卡器可能在认证早期拒绝模拟卡，或其 UI 不展示失败原因。"
            AccessDiagnosticConclusion.INCONCLUSIVE -> "观察到了部分 NFC 交互，但证据不足以唯一定位原因。建议再重复一次并与一张确定可用的门卡做基线对比。"
        }

        val evidence = buildList {
            if (signals.rfFieldSeen) add("检测到 13.56 MHz RF Field${if (fieldSessionCount > 0) " × $fieldSessionCount" else ""}")
            if (highestLayer > 0) add("RF 协议最高推进到 L$highestLayer")
            if (technologies.isNotEmpty()) add("Reader 技术：${technologies.sorted().joinToString(" / ")}")
            if (nfceeActionCount > 0) add("eSE/NFCEE action × $nfceeActionCount")
            if (cardActivationCount > 0) add("eSE 卡激活事件 × $cardActivationCount")
            if (nfceeAidObserved) add("观察到 NFCEE AID 交互")
            if (hciAidObserved) add("观察到 HCI transaction AID")
            when (activeCardMatched) {
                true -> add("底层 AID 证据与当前激活门卡匹配")
                false -> add("观察到卡片 AID，但未匹配当前激活门卡")
                null -> Unit
            }
            if (readerFingerprintObserved) add("小米 Reader Detector 生成/更新了读卡器指纹")
            if (isEmpty()) add("本次没有获得可用的 Xiaomi Reader Detector 证据")
        }

        val recommendations = when (conclusion) {
            AccessDiagnosticConclusion.SUCCESS -> listOf(
                "保留这次记录作为“可用门禁”基线；之后可与小区门禁记录直接对比。",
            )
            AccessDiagnosticConclusion.NO_RF_FIELD -> listOf(
                "再确认一次手机 NFC 天线位置并保持 2–3 秒。",
                "如果公司门禁能检测到 RF Field、而小区连续检测不到，强烈支持“小区实体卡为双频卡/门禁实际使用 125 kHz”这一判断。",
            )
            AccessDiagnosticConclusion.RF_FIELD_NO_CARD_INTERACTION -> listOf(
                "这已经排除纯 125 kHz 的可能性，下一步应比较公司门禁与小区门禁的协议层和 Reader 指纹。",
                "若小区始终停在 L1/L2，而公司能到 L3/L4，优先检查读卡器协议/调制兼容。",
            )
            AccessDiagnosticConclusion.CARD_INTERACTION_AUTH_FAILED,
            AccessDiagnosticConclusion.CARD_INTERACTION_NO_READER_FEEDBACK -> listOf(
                "手机 NFC、13.56 MHz 频段和 eSE 链路基本正常。",
                "下一步重点比较 MIFARE Sector 认证、卡片数据以及门禁的防复制策略；重新复制同一张卡通常不会解决这种差异。",
            )
            AccessDiagnosticConclusion.INCONCLUSIVE -> listOf(
                "重复一次现场诊断，并优先再采集一条“公司卡成功开门”的基线记录。",
            )
        }

        return AccessDiagnosticReport(
            sessionId = sessionId,
            providerId = XiaomiAccessDiagnosticProbe.PROVIDER_ID,
            startedAtMs = startedAtMs,
            finishedAtMs = finishedAtMs,
            cardTitle = cardTitle,
            cardSourceLabel = cardSourceLabel,
            outcome = outcome,
            conclusion = conclusion,
            summary = summary,
            evidence = evidence,
            recommendations = recommendations,
            signals = signals,
        )
    }

    private fun parseReaderMap(dump: String): Map<String, String> = dump.lineSequence()
        .filter { it.startsWith("ID:") }
        .mapNotNull { line -> readerIdRegex.find(line)?.groupValues?.getOrNull(1)?.let { it to line } }
        .toMap()

    private fun hasNonEmptyList(line: String, key: String): Boolean {
        val content = Regex("\\b${Regex.escape(key)}:\\[([^]]*)]")
            .find(line)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(",", "")
            ?.trim()
            .orEmpty()
        return content.isNotEmpty()
    }
}
