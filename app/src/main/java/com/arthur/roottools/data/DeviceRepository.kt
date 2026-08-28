package com.arthur.roottools.data

import com.arthur.roottools.model.CpuCluster
import com.arthur.roottools.model.DeviceSnapshot
import com.arthur.roottools.model.PressureMetric
import com.arthur.roottools.model.RuntimePressureSnapshot
import com.arthur.roottools.root.RootShell

class DeviceRepository(
    private val shell: RootShell,
) {
    suspend fun readSnapshot(): DeviceSnapshot {
        val command = """
            echo '__MODEL__'
            getprop ro.product.model
            echo '__THERMAL__'
            dumpsys thermalservice 2>/dev/null | grep -E 'Thermal Status:|Current temperatures from HAL:|Current cooling devices from HAL:|Temperature\{' | head -n 160
            echo '__BATTERY__'
            dumpsys battery 2>/dev/null | grep -E '^  (AC powered|USB powered|Wireless powered|level|temperature):'
            echo '__MEMORY__'
            grep -E '^(MemTotal|MemAvailable|SwapTotal|SwapFree):' /proc/meminfo
            echo '__MEM_PSI__'
            cat /proc/pressure/memory 2>/dev/null || true
            echo '__IO_PSI__'
            cat /proc/pressure/io 2>/dev/null || true
            echo '__CPU_PSI__'
            cat /proc/pressure/cpu 2>/dev/null || true
            echo '__CPU__'
            for d in /sys/devices/system/cpu/cpufreq/policy*; do
              [ -d "${'$'}d" ] || continue
              id=${'$'}{d##*policy}
              echo "POLICY=${'$'}id"
              echo "CPUS=${'$'}(cat ${'$'}d/related_cpus 2>/dev/null)"
              echo "HMIN=${'$'}(cat ${'$'}d/cpuinfo_min_freq 2>/dev/null)"
              echo "HMAX=${'$'}(cat ${'$'}d/cpuinfo_max_freq 2>/dev/null)"
              echo "SMIN=${'$'}(cat ${'$'}d/scaling_min_freq 2>/dev/null)"
              echo "SMAX=${'$'}(cat ${'$'}d/scaling_max_freq 2>/dev/null)"
              echo "CUR=${'$'}(cat ${'$'}d/scaling_cur_freq 2>/dev/null)"
              echo "AVAIL=${'$'}(cat ${'$'}d/scaling_available_frequencies 2>/dev/null)"
            done
        """.trimIndent()

        val result = shell.execute(command, timeoutSeconds = 10)
        if (!result.success) return DeviceSnapshot(rootAvailable = false)
        return parseSnapshot(result.output)
    }

    private fun parseSnapshot(raw: String): DeviceSnapshot {
        val sections = splitSections(raw)
        val model = sections["MODEL"]?.firstOrNull()?.trim().orEmpty().ifBlank { "Android" }
        val thermal = ThermalProbeParser.parse(sections["THERMAL"].orEmpty())
        val battery = parseBattery(sections["BATTERY"].orEmpty())
        val clusters = parseCpu(sections["CPU"].orEmpty())
        val runtimePressure = parseRuntimePressure(
            memoryLines = sections["MEMORY"].orEmpty(),
            memoryPressureLines = sections["MEM_PSI"].orEmpty(),
            ioPressureLines = sections["IO_PSI"].orEmpty(),
            cpuPressureLines = sections["CPU_PSI"].orEmpty(),
        )
        val adbLines = sections["ADB"].orEmpty()
        val port = adbLines.firstOrNull { it.startsWith("PORT=") }
            ?.substringAfter('=')?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        val listening = adbLines.any { it.trim() == "LISTENING=1" }
        val tailscale = sections["TAILSCALE"]?.firstOrNull()?.trim()?.ifBlank { null }

        return DeviceSnapshot(
            rootAvailable = true,
            model = model,
            thermalStatus = thermal.status,
            apTempC = thermal.apC,
            skinTempC = thermal.skinC,
            batteryTempC = thermal.batteryC,
            batteryLevel = battery.level,
            charging = battery.charging,
            cpuClusters = clusters,
            adbPort = port,
            adbListening = listening,
            tailscaleIpv4 = tailscale,
            runtimePressure = runtimePressure,
        )
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
                current?.let { section -> result.getOrPut(section) { mutableListOf() }.add(line) }
            }
        }
        return result
    }

    private data class BatteryData(var level: Int? = null, var charging: Boolean = false)

    private fun parseBattery(lines: List<String>): BatteryData {
        val data = BatteryData()
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("level:") -> data.level = trimmed.substringAfter(':').trim().toIntOrNull()
                trimmed.startsWith("AC powered:") || trimmed.startsWith("USB powered:") || trimmed.startsWith("Wireless powered:") -> {
                    data.charging = data.charging || trimmed.substringAfter(':').trim().equals("true", ignoreCase = true)
                }
            }
        }
        return data
    }

    private fun parseRuntimePressure(
        memoryLines: List<String>,
        memoryPressureLines: List<String>,
        ioPressureLines: List<String>,
        cpuPressureLines: List<String>,
    ): RuntimePressureSnapshot {
        val memory = memoryLines.associate { line ->
            line.substringBefore(':') to
                (line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L)
        }
        return RuntimePressureSnapshot(
            memory = parsePressure(memoryPressureLines),
            io = parsePressure(ioPressureLines),
            cpu = parsePressure(cpuPressureLines),
            memTotalKb = memory["MemTotal"] ?: 0,
            memAvailableKb = memory["MemAvailable"] ?: 0,
            swapTotalKb = memory["SwapTotal"] ?: 0,
            swapFreeKb = memory["SwapFree"] ?: 0,
        )
    }

    private fun parsePressure(lines: List<String>): PressureMetric {
        fun parse(prefix: String): Map<String, Float> = lines.firstOrNull { it.startsWith(prefix) }
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.drop(1)
            ?.mapNotNull { token ->
                val key = token.substringBefore('=', "")
                val value = token.substringAfter('=', "").toFloatOrNull()
                if (key.isNotBlank() && value != null) key to value else null
            }
            ?.toMap()
            .orEmpty()
        val some = parse("some ")
        val full = parse("full ")
        return PressureMetric(
            someAvg10 = some["avg10"] ?: 0f,
            someAvg60 = some["avg60"] ?: 0f,
            someAvg300 = some["avg300"] ?: 0f,
            fullAvg10 = full["avg10"] ?: 0f,
            fullAvg60 = full["avg60"] ?: 0f,
            fullAvg300 = full["avg300"] ?: 0f,
        )
    }

    private fun parseCpu(lines: List<String>): List<CpuCluster> {
        val blocks = mutableListOf<MutableMap<String, String>>()
        var current: MutableMap<String, String>? = null
        lines.forEach { line ->
            val key = line.substringBefore('=', "")
            val value = line.substringAfter('=', "")
            if (key == "POLICY") {
                val block = linkedMapOf("POLICY" to value)
                current = block
                blocks += block
            } else if (key.isNotEmpty()) {
                current?.set(key, value)
            }
        }
        return blocks.mapNotNull { map ->
            val id = map["POLICY"]?.toIntOrNull() ?: return@mapNotNull null
            val hardwareMax = map["HMAX"]?.toLongOrNull() ?: return@mapNotNull null
            CpuCluster(
                policyId = id,
                relatedCpus = map["CPUS"].orEmpty(),
                hardwareMinKHz = map["HMIN"]?.toLongOrNull() ?: 0L,
                hardwareMaxKHz = hardwareMax,
                scalingMinKHz = map["SMIN"]?.toLongOrNull() ?: 0L,
                scalingMaxKHz = map["SMAX"]?.toLongOrNull() ?: hardwareMax,
                currentKHz = map["CUR"]?.toLongOrNull() ?: 0L,
                availableKHz = map["AVAIL"].orEmpty().split(' ').mapNotNull { it.toLongOrNull() },
            )
        }.sortedBy { it.policyId }
    }
}

