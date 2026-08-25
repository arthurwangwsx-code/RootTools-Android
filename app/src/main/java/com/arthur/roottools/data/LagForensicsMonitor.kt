package com.arthur.roottools.data

import com.arthur.roottools.model.DeviceSnapshot
import com.arthur.roottools.model.LagForensicsSample
import com.arthur.roottools.model.LagForensicsState
import com.arthur.roottools.model.LagIncidentSummary
import com.arthur.roottools.model.LagPressureLevel
import com.arthur.roottools.policy.LagForensicsPolicy
import com.arthur.roottools.root.RootShell
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LagForensicsMonitor(
    private val shell: RootShell,
    private val store: LagForensicsStore,
) {
    private val history = ArrayDeque<LagForensicsSample>()
    private val captureGate = AtomicBoolean(false)
    private var lastCaptureAtMs = store.readSummaries(1).firstOrNull()?.capturedAtMs ?: 0L
    private var consecutivePressureSamples = 0

    private val _state = MutableStateFlow(
        LagForensicsState(
            enabled = store.enabled,
            incidents = store.readSummaries(),
        ),
    )
    val state: StateFlow<LagForensicsState> = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        store.enabled = enabled
        if (!enabled) {
            consecutivePressureSamples = 0
            synchronized(history) { history.clear() }
        }
        _state.value = _state.value.copy(
            enabled = enabled,
            consecutivePressureSamples = consecutivePressureSamples,
        )
    }

    fun observe(snapshot: DeviceSnapshot, scope: CoroutineScope) {
        if (!snapshot.rootAvailable || !store.enabled) return
        val now = System.currentTimeMillis()
        val pressure = snapshot.runtimePressure
        val level = LagForensicsPolicy.level(pressure)
        consecutivePressureSamples = if (level == LagPressureLevel.NORMAL) 0 else consecutivePressureSamples + 1
        val sample = LagForensicsSample(
            timestampMs = now,
            level = level,
            memorySome10 = pressure.memory.someAvg10,
            memoryFull10 = pressure.memory.fullAvg10,
            ioSome10 = pressure.io.someAvg10,
            ioFull10 = pressure.io.fullAvg10,
            cpuSome10 = pressure.cpu.someAvg10,
            memAvailableRatio = pressure.memAvailableRatio,
            swapUsedKb = pressure.swapUsedKb,
            thermalStatus = snapshot.thermalStatus,
            skinTempC = snapshot.skinTempC,
        )
        synchronized(history) {
            history.addLast(sample)
            while (history.size > MAX_HISTORY_POINTS) history.removeFirst()
        }
        _state.value = _state.value.copy(
            enabled = true,
            latestSample = sample,
            consecutivePressureSamples = consecutivePressureSamples,
        )

        if (!LagForensicsPolicy.shouldCapture(level, consecutivePressureSamples, now, lastCaptureAtMs)) return
        if (!captureGate.compareAndSet(false, true)) return
        lastCaptureAtMs = now
        _state.value = _state.value.copy(captureInProgress = true)
        val historySnapshot = synchronized(history) { history.toList() }
        scope.launch {
            try {
                captureIncident(snapshot, sample, historySnapshot)
            } finally {
                captureGate.set(false)
                _state.value = _state.value.copy(captureInProgress = false)
            }
        }
    }

    fun readLatestEvidence(): String = store.readLatestEvidence()

    private suspend fun captureIncident(
        snapshot: DeviceSnapshot,
        sample: LagForensicsSample,
        historySnapshot: List<LagForensicsSample>,
    ) {
        val evidenceResult = shell.execute(EVIDENCE_COMMAND, timeoutSeconds = EVIDENCE_TIMEOUT_SECONDS)
        val evidence = buildString {
            appendLine("captureSuccess=${evidenceResult.success}")
            appendLine("captureTimedOut=${evidenceResult.timedOut}")
            appendLine("thermalStatus=${snapshot.thermalStatus}")
            appendLine("skinTempC=${snapshot.skinTempC ?: ""}")
            appendLine("apTempC=${snapshot.apTempC ?: ""}")
            append(evidenceResult.output.take(MAX_EVIDENCE_CHARS))
        }
        val reason = buildReason(sample)
        val summary = LagIncidentSummary(
            capturedAtMs = sample.timestampMs,
            level = sample.level,
            reason = reason,
            memorySome10 = sample.memorySome10,
            memoryFull10 = sample.memoryFull10,
            ioSome10 = sample.ioSome10,
            ioFull10 = sample.ioFull10,
            cpuSome10 = sample.cpuSome10,
            memAvailableRatio = sample.memAvailableRatio,
            evidenceFileName = "${LagForensicsStore.FILE_PREFIX}${sample.timestampMs}.txt",
        )
        store.writeIncident(summary, historySnapshot, evidence)
        _state.value = _state.value.copy(incidents = store.readSummaries())
    }

    private fun buildReason(sample: LagForensicsSample): String = buildList {
        if (sample.memorySome10 >= 10f || sample.memoryFull10 >= 2f) add("memory PSI")
        if (sample.ioSome10 >= 10f || sample.ioFull10 >= 3f) add("IO PSI")
        if (sample.cpuSome10 >= 40f) add("CPU PSI")
        if (sample.memAvailableRatio < 0.10f) add("low MemAvailable")
    }.ifEmpty { listOf("system pressure") }.joinToString(" + ")

    private companion object {
        const val MAX_HISTORY_POINTS = 20
        const val EVIDENCE_TIMEOUT_SECONDS = 5L
        const val MAX_EVIDENCE_CHARS = 96_000
        val EVIDENCE_COMMAND = """
            echo '__META__'
            date
            uptime
            echo '__PRESSURE__'
            echo MEMORY; cat /proc/pressure/memory 2>/dev/null
            echo IO; cat /proc/pressure/io 2>/dev/null
            echo CPU; cat /proc/pressure/cpu 2>/dev/null
            echo '__MEMORY__'
            grep -E '^(MemTotal|MemAvailable|Cached|AnonPages|Slab|SwapTotal|SwapFree|Dirty|Writeback):' /proc/meminfo
            cat /sys/block/zram0/mm_stat 2>/dev/null | head -n 1
            echo '__D_STATE__'
            ps -A -o PID,PPID,USER,STAT,ELAPSED,ARGS 2>/dev/null | awk 'NR == 1 || ${'$'}4 ~ /^D/' | head -n 48
            echo '__TOP__'
            top -b -n 1 -m 18 -s 3 -o PID,USER,%CPU,%MEM,RES,ARGS 2>/dev/null | head -n 28
            echo '__CPU_POLICY__'
            for d in /sys/devices/system/cpu/cpufreq/policy*; do
              [ -d "${'$'}d" ] || continue
              echo "${'$'}{d##*policy} cur=${'$'}(cat ${'$'}d/scaling_cur_freq 2>/dev/null) max=${'$'}(cat ${'$'}d/scaling_max_freq 2>/dev/null) hw=${'$'}(cat ${'$'}d/cpuinfo_max_freq 2>/dev/null)"
            done
            echo '__RECENT_ANR__'
            ls -lt /data/anr 2>/dev/null | head -n 10
            echo '__RECENT_DROPBOX__'
            ls -lt /data/system/dropbox 2>/dev/null | head -n 14
            echo '__KERNEL_TAIL__'
            dmesg 2>/dev/null | tail -n 260 | grep -Ei 'blocked|sysrq|f2fs|ufs|icc_|rpmh|timeout|stall|memlat|bwmon|writeback' | tail -n 120
        """.trimIndent()
    }
}
