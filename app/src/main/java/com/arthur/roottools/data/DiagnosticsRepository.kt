package com.arthur.roottools.data

import com.arthur.roottools.model.ActiveServiceHealth
import com.arthur.roottools.model.DeviceHealthSnapshot
import com.arthur.roottools.model.DiagnosticProcess
import com.arthur.roottools.model.DiagnosticsSnapshot
import com.arthur.roottools.model.RootShellAttribution
import com.arthur.roottools.model.RootShellDetails
import com.arthur.roottools.model.RootShellRecord
import com.arthur.roottools.model.WakeLockHealth
import com.arthur.roottools.root.RootShell
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticsRepository(private val shell: RootShell) {
    suspend fun collect(): DiagnosticsSnapshot {
        val result = shell.execute(COMMAND, timeoutSeconds = 15)
        if (!result.success) return DiagnosticsSnapshot()
        val sections = splitSections(result.output)
        val top = parseTop(sections["TOP"].orEmpty())
        val cpuByPid = top.associate { it.pid to it.cpuPercent }
        return DiagnosticsSnapshot(
            topProcesses = top,
            rootShells = parseRootShells(sections["ROOT_SHELLS"].orEmpty(), cpuByPid),
            wakeLocks = parseWakeLocks(sections["WAKELOCKS"].orEmpty(), sections["WAKELOCK_HISTORY"].orEmpty()),
            services = parseServices(sections["SERVICES"].orEmpty(), sections["THIRD"].orEmpty()),
        )
    }

    suspend fun attributeRootShell(pid: Int): RootShellDetails {
        if (pid <= 0) return RootShellDetails(pid)
        val command = deepCommand(pid)
        val result = shell.execute(command, timeoutSeconds = 8)
        if (!result.success && result.output.isBlank()) return RootShellDetails(pid)
        val sections = splitSections(result.output)
        val meta = sections["DETAIL"].orEmpty()
        val owners = sections["OWNERS"].orEmpty().mapNotNull { line ->
            val parts = line.split('|', limit = 4)
            if (parts.size < 4) return@mapNotNull null
            RootShellAttribution(
                pipe = parts[0],
                ownerPid = parts[1].toIntOrNull() ?: return@mapNotNull null,
                ownerFd = parts[2].toIntOrNull() ?: return@mapNotNull null,
                ownerCommand = parts[3],
            )
        }.distinctBy { Triple(it.pipe, it.ownerPid, it.ownerFd) }
        return RootShellDetails(
            pid = pid,
            command = meta.value("CMD"),
            fd0 = meta.value("FD0"),
            fd1 = meta.value("FD1"),
            fd2 = meta.value("FD2"),
            rchar = meta.value("RCHAR").toLongOrNull() ?: 0,
            syscr = meta.value("SYSCR").toLongOrNull() ?: 0,
            readBytes = meta.value("READ_BYTES").toLongOrNull() ?: 0,
            attributions = owners,
        )
    }

    fun buildSnapshotText(health: DeviceHealthSnapshot, diagnostics: DiagnosticsSnapshot): String = buildString {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        appendLine("Root Tools Diagnostic Snapshot")
        appendLine("Captured: $date")
        appendLine()
        appendLine("[THERMAL]")
        appendLine("status=${health.thermal.status} AP=${health.thermal.apC} Skin=${health.thermal.skinC} Battery=${health.thermal.batteryC}")
        appendLine("[CPU]")
        appendLine("usage=${"%.1f".format(health.cpuUsagePercent)} load=${health.load1}/${health.load5}/${health.load15}")
        health.cpuClusters.forEach { appendLine("policy${it.policyId} cpus=${it.relatedCpus} cur=${it.currentKHz} max=${it.scalingMaxKHz} hw=${it.hardwareMaxKHz} gov=${it.governor}") }
        appendLine("[MEMORY]")
        appendLine("availableKb=${health.memory.availableKb} totalKb=${health.memory.totalKb} swapUsedKb=${health.memory.swapUsedKb} psiSome10=${health.memory.pressure.someAvg10}")
        appendLine("[TOP]")
        diagnostics.topProcesses.take(12).forEach { appendLine("${it.pid} ${it.user} cpu=${it.cpuPercent}% mem=${it.memoryPercent}% rss=${it.rss} ${it.processName}") }
        appendLine("[ROOT_SHELL]")
        diagnostics.rootShells.forEach { appendLine("pid=${it.pid} ppid=${it.ppid} cpu=${it.cpuPercent}% elapsed=${it.elapsed} cmd=${it.command}") }
        appendLine("[WAKELOCK]")
        appendLine("active=${diagnostics.wakeLocks.activeCount}")
        diagnostics.wakeLocks.activeLines.take(8).forEach(::appendLine)
        appendLine("[SERVICES]")
        diagnostics.services.take(30).forEach { appendLine("${it.packageName}/${it.component} foreground=${it.foreground}") }
    }

    private fun parseTop(lines: List<String>): List<DiagnosticProcess> = lines.mapNotNull { line ->
        val parts = line.split('|', limit = 7)
        if (parts.size < 7) return@mapNotNull null
        DiagnosticProcess(
            pid = parts[0].toIntOrNull() ?: return@mapNotNull null,
            user = parts[1],
            ppid = parts[2].toIntOrNull() ?: 0,
            cpuPercent = parts[3].toFloatOrNull() ?: 0f,
            memoryPercent = parts[4].toFloatOrNull() ?: 0f,
            rss = parts[5],
            processName = parts[6],
        )
    }

    private fun parseRootShells(lines: List<String>, cpuByPid: Map<Int, Float>): List<RootShellRecord> = lines.mapNotNull { line ->
        val parts = line.split('|', limit = 5)
        if (parts.size < 5) return@mapNotNull null
        val pid = parts[1].toIntOrNull() ?: return@mapNotNull null
        RootShellRecord(
            pid = pid,
            ppid = parts[2].toIntOrNull() ?: 0,
            elapsed = parts[3],
            command = parts[4],
            cpuPercent = cpuByPid[pid] ?: 0f,
        )
    }

    private fun parseWakeLocks(active: List<String>, history: List<String>): WakeLockHealth {
        val count = active.firstOrNull { it.contains("Wake Locks: size=") }
            ?.substringAfter("size=")?.trim()?.toIntOrNull() ?: 0
        return WakeLockHealth(
            activeCount = count,
            activeLines = active.drop(1).map(String::trim).filter { it.isNotBlank() && !it.startsWith("Suspend Blockers") }.take(16),
            recentLines = history.map(String::trim).filter { it.isNotBlank() && !it.startsWith("WakeLock Release History") }.take(16),
        )
    }

    private fun parseServices(lines: List<String>, thirdLines: List<String>): List<ActiveServiceHealth> {
        val third = thirdLines.map { it.removePrefix("package:").trim() }.toSet()
        val result = mutableListOf<ActiveServiceHealth>()
        var pendingIndex: Int? = null
        lines.forEach { line ->
            val match = SERVICE_REGEX.find(line)
            if (match != null) {
                val pkg = match.groupValues[1]
                if (pkg in third) {
                    result += ActiveServiceHealth(pkg, match.groupValues[2], foreground = false)
                    pendingIndex = result.lastIndex
                } else pendingIndex = null
            } else if (line.contains("isForeground=true")) {
                val index = pendingIndex ?: return@forEach
                result[index] = result[index].copy(foreground = true)
            }
        }
        return result.distinctBy { it.packageName to it.component }
    }

    private fun List<String>.value(key: String): String = firstOrNull { it.startsWith("$key=") }?.substringAfter('=')?.trim().orEmpty()

    private fun splitSections(raw: String): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableList<String>>()
        var current: String? = null
        raw.lineSequence().forEach { line ->
            if (line.startsWith("__") && line.endsWith("__")) {
                val section = line.removePrefix("__").removeSuffix("__")
                current = section
                result.getOrPut(section) { mutableListOf() }
            } else current?.let { result.getOrPut(it) { mutableListOf() }.add(line) }
        }
        return result
    }

    private fun deepCommand(pid: Int): String = """
        echo '__DETAIL__'
        echo CMD=${'$'}(head -c 180 /proc/$pid/cmdline 2>/dev/null | tr '\0' ' ')
        echo FD0=${'$'}(readlink /proc/$pid/fd/0 2>/dev/null)
        echo FD1=${'$'}(readlink /proc/$pid/fd/1 2>/dev/null)
        echo FD2=${'$'}(readlink /proc/$pid/fd/2 2>/dev/null)
        echo RCHAR=${'$'}(awk '/^rchar:/{print ${'$'}2}' /proc/$pid/io 2>/dev/null)
        echo SYSCR=${'$'}(awk '/^syscr:/{print ${'$'}2}' /proc/$pid/io 2>/dev/null)
        echo READ_BYTES=${'$'}(awk '/^read_bytes:/{print ${'$'}2}' /proc/$pid/io 2>/dev/null)
        echo '__OWNERS__'
        for pipe in ${'$'}(for fd in 0 1 2; do readlink /proc/$pid/fd/${'$'}fd 2>/dev/null; done | grep '^pipe:' | sort -u); do
          count=0
          for f in /proc/[0-9]*/fd/*; do
            [ ${'$'}count -ge 16 ] && break
            link=${'$'}(readlink "${'$'}f" 2>/dev/null) || continue
            [ "${'$'}link" = "${'$'}pipe" ] || continue
            opid=${'$'}(echo "${'$'}f" | cut -d/ -f3)
            [ "${'$'}opid" = "$pid" ] && continue
            ofd=${'$'}{f##*/}
            ocmd=${'$'}(head -c 160 /proc/${'$'}opid/cmdline 2>/dev/null | tr '\0' ' ')
            [ -n "${'$'}ocmd" ] || ocmd=${'$'}(cat /proc/${'$'}opid/comm 2>/dev/null)
            echo "${'$'}pipe|${'$'}opid|${'$'}ofd|${'$'}ocmd"
            count=${'$'}((count+1))
          done
        done
    """.trimIndent()

    private companion object {
        val SERVICE_REGEX = Regex("ServiceRecord\\{[^}]* u0 ([A-Za-z0-9._]+)/([^ }]+)")
        val COMMAND = """
            echo '__TOP__'
            top -b -n 1 -m 20 -s 4 -o PID,USER,PPID,%CPU,%MEM,RES,ARGS 2>/dev/null | awk '${'$'}1 ~ /^[0-9]+${'$'}/ {printf "%s|%s|%s|%s|%s|%s|%s\\n",${'$'}1,${'$'}2,${'$'}3,${'$'}4,${'$'}5,${'$'}6,${'$'}7}'
            echo '__ROOT_SHELLS__'
            ps -A -o USER,PID,PPID,ELAPSED,ARGS 2>/dev/null | awk '${'$'}1=="root" && (${'$'}5=="sh" || ${'$'}5 ~ /\/sh${'$'}/ || ${'$'}0 ~ /libbusybox.so sh/) {printf "%s|%s|%s|%s|%s\\n",${'$'}1,${'$'}2,${'$'}3,${'$'}4,${'$'}5}'
            echo '__WAKELOCKS__'
            dumpsys power 2>/dev/null | sed -n '/Wake Locks: size=/,/Suspend Blockers:/p' | head -n 60
            echo '__WAKELOCK_HISTORY__'
            dumpsys power 2>/dev/null | sed -n '/WakeLock Release History:/,/DisplayGroup History:/p' | head -n 40
            echo '__THIRD__'
            pm list packages -3
            echo '__SERVICES__'
            dumpsys activity services 2>/dev/null | grep -E 'ServiceRecord\\{|isForeground=true' | head -n 900
        """.trimIndent()
    }
}
