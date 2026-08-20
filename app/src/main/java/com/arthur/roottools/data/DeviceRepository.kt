package com.arthur.roottools.data

import com.arthur.roottools.model.CpuCluster
import com.arthur.roottools.model.DeviceSnapshot
import com.arthur.roottools.root.RootShell

class DeviceRepository(
    private val shell: RootShell,
) {
    suspend fun readSnapshot(): DeviceSnapshot {
        val root = shell.isAvailable()
        if (!root) return DeviceSnapshot(rootAvailable = false)

        val command = """
            echo '__MODEL__'
            getprop ro.product.model
            echo '__THERMAL__'
            dumpsys thermalservice 2>/dev/null | grep -E 'Thermal Status:|mName=(AP|BAT|SKIN)' | head -n 10
            echo '__BATTERY__'
            dumpsys battery 2>/dev/null | grep -E '^  (AC powered|USB powered|Wireless powered|level|temperature):'
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
        if (!result.success) return DeviceSnapshot(rootAvailable = true)
        return parseSnapshot(result.output)
    }

    private fun parseSnapshot(raw: String): DeviceSnapshot {
        val sections = splitSections(raw)
        val model = sections["MODEL"]?.firstOrNull()?.trim().orEmpty().ifBlank { "Android" }
        val thermal = parseThermal(sections["THERMAL"].orEmpty())
        val battery = parseBattery(sections["BATTERY"].orEmpty())
        val clusters = parseCpu(sections["CPU"].orEmpty())
        val adbLines = sections["ADB"].orEmpty()
        val port = adbLines.firstOrNull { it.startsWith("PORT=") }
            ?.substringAfter('=')?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        val listening = adbLines.any { it.trim() == "LISTENING=1" }
        val tailscale = sections["TAILSCALE"]?.firstOrNull()?.trim()?.ifBlank { null }

        return DeviceSnapshot(
            rootAvailable = true,
            model = model,
            thermalStatus = thermal.status,
            apTempC = thermal.ap,
            skinTempC = thermal.skin,
            batteryTempC = thermal.battery,
            batteryLevel = battery.level,
            charging = battery.charging,
            cpuClusters = clusters,
            adbPort = port,
            adbListening = listening,
            tailscaleIpv4 = tailscale,
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

    private data class ThermalData(
        var status: Int = 0,
        var ap: Float? = null,
        var skin: Float? = null,
        var battery: Float? = null,
    )

    private fun parseThermal(lines: List<String>): ThermalData {
        val data = ThermalData()
        lines.forEach { line ->
            if (line.contains("Thermal Status:")) {
                data.status = line.substringAfter("Thermal Status:").trim().toIntOrNull() ?: data.status
            }
            val value = Regex("mValue=([0-9.]+)").find(line)?.groupValues?.getOrNull(1)?.toFloatOrNull()
            when {
                line.contains("mName=AP") -> data.ap = value
                line.contains("mName=SKIN") -> data.skin = value
                line.contains("mName=BAT") -> data.battery = value
            }
        }
        return data
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

