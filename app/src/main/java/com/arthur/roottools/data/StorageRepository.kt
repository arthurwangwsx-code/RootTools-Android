package com.arthur.roottools.data

import com.arthur.roottools.model.BlockDeviceStat
import com.arthur.roottools.model.FileSystemUsage
import com.arthur.roottools.model.PressureMetric
import com.arthur.roottools.model.StorageSnapshot
import com.arthur.roottools.root.RootShell

class StorageRepository(private val shell: RootShell) {
    suspend fun read(): StorageSnapshot {
        val result = shell.execute(COMMAND, timeoutSeconds = 8)
        if (!result.success) return StorageSnapshot()
        val sections = splitSections(result.output)
        return StorageSnapshot(
            fileSystems = buildList {
                parseDf("Data", sections["DATA"].orEmpty())?.let(::add)
                parseDf("Shared", sections["SHARED"].orEmpty())?.let { shared ->
                    if (none { it.totalKb == shared.totalKb && it.usedKb == shared.usedKb && it.availableKb == shared.availableKb }) add(shared)
                    else add(shared)
                }
            },
            ioPressure = parsePressure(sections["IO_PSI"].orEmpty()),
            blockDevices = parseBlocks(sections["BLOCK"].orEmpty()),
        )
    }

    private fun parseDf(label: String, lines: List<String>): FileSystemUsage? {
        val line = lines.lastOrNull { it.trim().startsWith("/") } ?: return null
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 6) return null
        return FileSystemUsage(
            label = label,
            filesystem = parts[0],
            totalKb = parts[1].toLongOrNull() ?: return null,
            usedKb = parts[2].toLongOrNull() ?: 0,
            availableKb = parts[3].toLongOrNull() ?: 0,
            usedPercent = parts[4].removeSuffix("%").toIntOrNull() ?: 0,
            mountedOn = parts.drop(5).joinToString(" "),
        )
    }

    private fun parseBlocks(lines: List<String>): List<BlockDeviceStat> = lines.mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 12) return@mapNotNull null
        val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
        if (values.size < 11) return@mapNotNull null
        BlockDeviceStat(
            name = parts[0],
            readsCompleted = values[0],
            sectorsRead = values[2],
            writesCompleted = values[4],
            sectorsWritten = values[6],
            ioTimeMs = values[9],
        )
    }.sortedByDescending { it.sectorsRead + it.sectorsWritten }

    private fun parsePressure(lines: List<String>): PressureMetric {
        fun row(prefix: String): Map<String, Float> = lines.firstOrNull { it.startsWith(prefix) }
            ?.split(Regex("\\s+"))?.drop(1)?.mapNotNull { token ->
                val key = token.substringBefore('=', "")
                val value = token.substringAfter('=', "").toFloatOrNull()
                if (key.isNotBlank() && value != null) key to value else null
            }?.toMap().orEmpty()
        val some = row("some ")
        val full = row("full ")
        return PressureMetric(
            someAvg10 = some["avg10"] ?: 0f,
            someAvg60 = some["avg60"] ?: 0f,
            someAvg300 = some["avg300"] ?: 0f,
            fullAvg10 = full["avg10"] ?: 0f,
            fullAvg60 = full["avg60"] ?: 0f,
            fullAvg300 = full["avg300"] ?: 0f,
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
            } else current?.let { result.getOrPut(it) { mutableListOf() }.add(line) }
        }
        return result
    }

    private companion object {
        val COMMAND = """
            echo '__DATA__'
            df -k /data 2>/dev/null
            echo '__SHARED__'
            df -k /storage/emulated/0 2>/dev/null
            echo '__IO_PSI__'
            cat /proc/pressure/io 2>/dev/null
            echo '__BLOCK__'
            for n in sda sdb sdc sdd sde sdf mmcblk0; do
              [ -f /sys/block/${'$'}n/stat ] || continue
              echo ${'$'}n ${'$'}(cat /sys/block/${'$'}n/stat)
            done
        """.trimIndent()
    }
}
