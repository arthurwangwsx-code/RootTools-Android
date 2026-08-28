package com.arthur.roottools.data

import com.arthur.roottools.model.BatteryHealth
import com.arthur.roottools.model.CpuHealthCluster
import com.arthur.roottools.model.DeviceHealthSnapshot
import com.arthur.roottools.model.LmkHealth
import com.arthur.roottools.model.MemoryProcessHealth
import com.arthur.roottools.model.MemoryHealth
import com.arthur.roottools.model.PressureMetric
import com.arthur.roottools.model.ProcessHealth
import com.arthur.roottools.model.SchedulerGroupHealth
import com.arthur.roottools.model.SchedulerHealth
import com.arthur.roottools.model.ThermalHealth
import com.arthur.roottools.root.RootShell

class DeviceHealthCollector(private val shell: RootShell) {
    private var previousCpuByName: Map<String, CpuTimes> = emptyMap()

    suspend fun collect(includeProcesses: Boolean): DeviceHealthSnapshot {
        val result = shell.execute(buildCommand(includeProcesses), timeoutSeconds = if (includeProcesses) 10 else 6)
        if (!result.success) return DeviceHealthSnapshot(rootAvailable = false)

        val sections = splitSections(result.output)
        val cpuTimesByName = parseCpuTimesByName(sections["CPU_STAT"].orEmpty())
        val totalUsage = calculateCpuUsage(cpuTimesByName["cpu"], previousCpuByName["cpu"])
        val perCoreUsage = cpuTimesByName
            .filterKeys { it.startsWith("cpu") && it != "cpu" }
            .mapValues { (name, current) -> calculateCpuUsage(current, previousCpuByName[name]).first }
        if (cpuTimesByName.isNotEmpty()) previousCpuByName = cpuTimesByName

        val loads = sections["LOAD"]?.firstOrNull().orEmpty().split(Regex("\\s+")).mapNotNull { it.toFloatOrNull() }

        return DeviceHealthSnapshot(
            rootAvailable = true,
            cpuUsagePercent = totalUsage.first,
            cpuIdlePercent = totalUsage.second,
            load1 = loads.getOrElse(0) { 0f },
            load5 = loads.getOrElse(1) { 0f },
            load15 = loads.getOrElse(2) { 0f },
            cpuClusters = parseClusters(sections["CPU_POLICY"].orEmpty(), perCoreUsage),
            scheduler = parseScheduler(sections["SCHEDULER"].orEmpty()),
            memory = parseMemory(
                sections["MEMINFO"].orEmpty(),
                sections["ZRAM"].orEmpty(),
                sections["MEM_PSI"].orEmpty(),
            ),
            ioPressure = parsePressure(sections["IO_PSI"].orEmpty()),
            thermal = parseThermal(sections["THERMAL"].orEmpty()),
            battery = parseBattery(sections["BATTERY"].orEmpty(), sections["PROTECTION"].orEmpty()),
            uptimeSeconds = sections["SYSTEM"]?.firstOrNull { it.startsWith("UPTIME=") }
                ?.substringAfter('=')?.substringBefore('.')?.toLongOrNull() ?: 0,
            processCount = sections["SYSTEM"]?.firstOrNull { it.startsWith("PROCESSES=") }
                ?.substringAfter('=')?.toIntOrNull() ?: 0,
            topProcesses = parseProcesses(sections["TOP"].orEmpty()),
            topMemoryProcesses = parseMemoryProcesses(sections["TOP_MEMORY"].orEmpty()),
            lmk = parseLmk(sections["LMK"].orEmpty(), sections["LMK_CONFIG"].orEmpty()),
        )
    }

    private fun buildCommand(includeProcesses: Boolean): String = buildString {
        appendLine("echo '__CPU_STAT__'")
        appendLine("grep -E '^cpu([0-9]+)? ' /proc/stat")
        appendLine("echo '__LOAD__'")
        appendLine("cat /proc/loadavg | awk '{print $1, $2, $3}'")
        appendLine("echo '__CPU_POLICY__'")
        appendLine("for d in /sys/devices/system/cpu/cpufreq/policy*; do [ -d \"\$d\" ] || continue; id=\${d##*policy}; echo \"POLICY=\$id\"; echo \"CPUS=\$(cat \$d/related_cpus 2>/dev/null)\"; echo \"CUR=\$(cat \$d/scaling_cur_freq 2>/dev/null)\"; echo \"SMIN=\$(cat \$d/scaling_min_freq 2>/dev/null)\"; echo \"SMAX=\$(cat \$d/scaling_max_freq 2>/dev/null)\"; echo \"HMAX=\$(cat \$d/cpuinfo_max_freq 2>/dev/null)\"; echo \"GOV=\$(cat \$d/scaling_governor 2>/dev/null)\"; done")
        appendLine("echo '__SCHEDULER__'")
        appendLine("for g in top-app foreground background system-background; do echo \"GROUP=\$g\"; echo \"CPUS=\$(cat /dev/cpuset/\$g/cpus 2>/dev/null)\"; echo \"UMIN=\$(cat /dev/cpuctl/\$g/cpu.uclamp.min 2>/dev/null)\"; echo \"UMAX=\$(cat /dev/cpuctl/\$g/cpu.uclamp.max 2>/dev/null)\"; done")
        appendLine("echo '__MEMINFO__'")
        appendLine("grep -E '^(MemTotal|MemAvailable|Cached|AnonPages|Slab|SwapTotal|SwapFree):' /proc/meminfo")
        appendLine("echo '__ZRAM__'")
        appendLine("cat /sys/block/zram0/mm_stat 2>/dev/null || true")
        appendLine("echo '__MEM_PSI__'")
        appendLine("cat /proc/pressure/memory 2>/dev/null || true")
        appendLine("echo '__IO_PSI__'")
        appendLine("cat /proc/pressure/io 2>/dev/null || true")
        appendLine("echo '__THERMAL__'")
        appendLine("dumpsys thermalservice 2>/dev/null | grep -E 'Thermal Status:|Current temperatures from HAL:|Current cooling devices from HAL:|Temperature\\{' | head -n 160")
        appendLine("echo '__BATTERY__'")
        appendLine("dumpsys battery 2>/dev/null | grep -E '^  (AC powered|USB powered|Wireless powered|level|voltage|temperature|current now):'")
        appendLine("echo '__PROTECTION__'")
        appendLine("echo PROTECT=\$(settings get global protect_battery 2>/dev/null); echo THRESHOLD=\$(settings get global battery_protection_threshold 2>/dev/null)")
        appendLine("echo '__SYSTEM__'")
        appendLine("echo UPTIME=\$(cut -d' ' -f1 /proc/uptime); echo PROCESSES=\$(ps -A | tail -n +2 | wc -l)")
        appendLine("echo '__TOP__'")
        if (includeProcesses) {
            appendLine("top -b -n 1 -s 3 -o PID,USER,%CPU,%MEM,RES,ARGS 2>/dev/null | awk '\$1 ~ /^[0-9]+\$/ {printf \"%s|%s|%s|%s|%s|%s\\n\",\$1,\$2,\$3,\$4,\$5,\$6}' | head -n 8")
        }
        appendLine("echo '__TOP_MEMORY__'")
        if (includeProcesses) {
            appendLine("top -b -n 1 -m 6 -s 3 -o PID,USER,RES,ARGS 2>/dev/null | awk '\$1 ~ /^[0-9]+\$/ {printf \"%s|%s|%s|%s\\n\",\$1,\$2,\$3,\$4}' | while IFS='|' read pid user rss cmd; do pss=\$(awk '/^Pss:/{print \$2}' /proc/\$pid/smaps_rollup 2>/dev/null); echo \"\$pid|\$user|\$rss|\${pss:-0}|\$cmd\"; done")
        }
        appendLine("echo '__LMK__'")
        if (includeProcesses) {
            appendLine("logcat -b events -d -t 200 -s am_kill:I 2>/dev/null | tail -n 80")
        }
        appendLine("echo '__LMK_CONFIG__'")
        if (includeProcesses) {
            appendLine("getprop | grep -E '\\[ro\\.(lmk|config.low_ram)|\\[sys\\.lmk' | head -n 40")
        }
    }

    private data class CpuTimes(val total: Long, val idle: Long)

    private fun parseCpuTimesByName(lines: List<String>): Map<String, CpuTimes> = lines.mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"))
        val name = parts.firstOrNull()?.takeIf { it == "cpu" || it.matches(Regex("cpu[0-9]+")) } ?: return@mapNotNull null
        val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
        if (values.size < 5) return@mapNotNull null
        name to CpuTimes(
            total = values.take(8).sum(),
            idle = values.getOrElse(3) { 0 } + values.getOrElse(4) { 0 },
        )
    }.toMap()

    private fun calculateCpuUsage(current: CpuTimes?, previous: CpuTimes?): Pair<Float, Float> {
        if (current == null || previous == null) return 0f to 100f
        val totalDelta = (current.total - previous.total).coerceAtLeast(1)
        val idleDelta = (current.idle - previous.idle).coerceAtLeast(0)
        val idle = (idleDelta * 100f / totalDelta).coerceIn(0f, 100f)
        return (100f - idle) to idle
    }

    private fun splitSections(raw: String): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableList<String>>()
        var current: String? = null
        raw.lineSequence().forEach { line ->
            if (line.startsWith("__") && line.endsWith("__")) {
                val section = line.removePrefix("__").removeSuffix("__")
                current = section
                result.getOrPut(section) { mutableListOf() }
            } else {
                current?.let { result.getOrPut(it) { mutableListOf() }.add(line) }
            }
        }
        return result
    }

    private fun parseClusters(lines: List<String>, perCoreUsage: Map<String, Float>): List<CpuHealthCluster> {
        val blocks = mutableListOf<MutableMap<String, String>>()
        var block: MutableMap<String, String>? = null
        lines.forEach { line ->
            val key = line.substringBefore('=', "")
            val value = line.substringAfter('=', "")
            if (key == "POLICY") {
                block = linkedMapOf("POLICY" to value).also { blocks += it }
            } else if (key.isNotBlank()) {
                block?.set(key, value)
            }
        }
        return blocks.mapNotNull { map ->
            val id = map["POLICY"]?.toIntOrNull() ?: return@mapNotNull null
            val related = map["CPUS"].orEmpty()
            val utilization = related.split(Regex("\\s+")).mapNotNull { cpu ->
                cpu.toIntOrNull()?.let { perCoreUsage["cpu$it"] }
            }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f
            CpuHealthCluster(
                policyId = id,
                relatedCpus = related,
                utilizationPercent = utilization,
                currentKHz = map["CUR"]?.toLongOrNull() ?: 0,
                scalingMinKHz = map["SMIN"]?.toLongOrNull() ?: 0,
                scalingMaxKHz = map["SMAX"]?.toLongOrNull() ?: 0,
                hardwareMaxKHz = map["HMAX"]?.toLongOrNull() ?: 0,
                governor = map["GOV"].orEmpty(),
            )
        }.sortedBy { it.policyId }
    }

    private fun parseScheduler(lines: List<String>): SchedulerHealth {
        val blocks = mutableListOf<MutableMap<String, String>>()
        var block: MutableMap<String, String>? = null
        lines.forEach { line ->
            val key = line.substringBefore('=', "")
            val value = line.substringAfter('=', "")
            if (key == "GROUP") block = linkedMapOf("GROUP" to value).also { blocks += it }
            else if (key.isNotBlank()) block?.set(key, value)
        }
        return SchedulerHealth(
            groups = blocks.mapNotNull { map ->
                val name = map["GROUP"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SchedulerGroupHealth(
                    name = name,
                    cpus = map["CPUS"].orEmpty(),
                    uclampMin = map["UMIN"]?.takeIf(String::isNotBlank),
                    uclampMax = map["UMAX"]?.takeIf(String::isNotBlank),
                )
            }
        )
    }

    private fun parseMemory(memLines: List<String>, zramLines: List<String>, pressureLines: List<String>): MemoryHealth {
        val mem = memLines.associate { line ->
            line.substringBefore(':') to (line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L)
        }
        val zram = zramLines.firstOrNull()?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toLongOrNull() }.orEmpty()
        return MemoryHealth(
            totalKb = mem["MemTotal"] ?: 0,
            availableKb = mem["MemAvailable"] ?: 0,
            cachedKb = mem["Cached"] ?: 0,
            anonKb = mem["AnonPages"] ?: 0,
            slabKb = mem["Slab"] ?: 0,
            swapTotalKb = mem["SwapTotal"] ?: 0,
            swapFreeKb = mem["SwapFree"] ?: 0,
            zramOriginalBytes = zram.getOrElse(0) { 0 },
            zramCompressedBytes = zram.getOrElse(1) { 0 },
            zramMemoryBytes = zram.getOrElse(2) { 0 },
            pressure = parsePressure(pressureLines),
        )
    }

    private fun parsePressure(lines: List<String>): PressureMetric {
        fun values(prefix: String): Map<String, Float> = lines.firstOrNull { it.startsWith(prefix) }
            ?.split(Regex("\\s+"))
            ?.drop(1)
            ?.mapNotNull { token ->
                val key = token.substringBefore('=', "")
                val value = token.substringAfter('=', "").toFloatOrNull()
                if (key.isNotBlank() && value != null) key to value else null
            }?.toMap().orEmpty()
        val some = values("some ")
        val full = values("full ")
        return PressureMetric(
            someAvg10 = some["avg10"] ?: 0f,
            someAvg60 = some["avg60"] ?: 0f,
            someAvg300 = some["avg300"] ?: 0f,
            fullAvg10 = full["avg10"] ?: 0f,
            fullAvg60 = full["avg60"] ?: 0f,
            fullAvg300 = full["avg300"] ?: 0f,
        )
    }

    private fun parseThermal(lines: List<String>): ThermalHealth {
        val thermal = ThermalProbeParser.parse(lines)
        return ThermalHealth(
            status = thermal.status,
            apC = thermal.apC,
            skinC = thermal.skinC,
            batteryC = thermal.batteryC,
            usbC = thermal.usbC,
            pathmC = thermal.pathmC,
        )
    }

    private fun parseBattery(lines: List<String>, protection: List<String>): BatteryHealth {
        var level: Int? = null
        var charging = false
        var voltage: Int? = null
        var current: Int? = null
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("level:") -> level = trimmed.substringAfter(':').trim().toIntOrNull()
                trimmed.startsWith("voltage:") -> voltage = trimmed.substringAfter(':').trim().toIntOrNull()
                trimmed.startsWith("current now:") -> current = trimmed.substringAfter(':').trim().toIntOrNull()
                trimmed.startsWith("AC powered:") || trimmed.startsWith("USB powered:") || trimmed.startsWith("Wireless powered:") ->
                    charging = charging || trimmed.substringAfter(':').trim().equals("true", true)
            }
        }
        val protect = protection.firstOrNull { it.startsWith("PROTECT=") }?.substringAfter('=')?.trim() == "1"
        val threshold = protection.firstOrNull { it.startsWith("THRESHOLD=") }?.substringAfter('=')?.trim()?.toIntOrNull()
        return BatteryHealth(level, charging, voltage, current, protect, threshold)
    }

    private fun parseProcesses(lines: List<String>): List<ProcessHealth> = lines.mapNotNull { line ->
        val parts = line.split('|', limit = 6)
        if (parts.size < 6) return@mapNotNull null
        ProcessHealth(
            pid = parts[0].toIntOrNull() ?: return@mapNotNull null,
            user = parts[1],
            cpuPercent = parts[2].toFloatOrNull() ?: 0f,
            memoryPercent = parts[3].toFloatOrNull() ?: 0f,
            rss = parts[4],
            processName = parts[5],
        )
    }

    private fun parseMemoryProcesses(lines: List<String>): List<MemoryProcessHealth> = lines.mapNotNull { line ->
        val parts = line.split('|', limit = 5)
        if (parts.size < 5) return@mapNotNull null
        MemoryProcessHealth(
            pid = parts[0].toIntOrNull() ?: return@mapNotNull null,
            user = parts[1],
            rssKb = parseMemorySizeKb(parts[2]),
            pssKb = parts[3].toLongOrNull() ?: 0L,
            processName = parts[4],
        )
    }

    private fun parseMemorySizeKb(raw: String): Long {
        val trimmed = raw.trim()
        val suffix = trimmed.lastOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }
        val number = (if (suffix != null) trimmed.dropLast(1) else trimmed).toDoubleOrNull() ?: return 0L
        return when (suffix) {
            'G' -> (number * 1024 * 1024).toLong()
            'M' -> (number * 1024).toLong()
            'K' -> number.toLong()
            else -> number.toLong()
        }
    }

    private fun parseLmk(lines: List<String>, configLines: List<String>): LmkHealth {
        val killLines = lines.filter { it.contains("am_kill") }
        val chimera = killLines.filter { it.contains("LMKD", ignoreCase = true) }
        val config = configLines.mapNotNull { line ->
            val match = Regex("\\[([^]]+)]\\s*:\\s*\\[([^]]*)]").find(line) ?: return@mapNotNull null
            match.groupValues[1] to match.groupValues[2]
        }.toMap()
        return LmkHealth(
            recentKillCount = killLines.size,
            chimeraKillCount = chimera.size,
            recentReasons = killLines.takeLast(8).map { it.substringAfter("am_kill", it).trim().take(180) },
            config = config,
        )
    }
}
