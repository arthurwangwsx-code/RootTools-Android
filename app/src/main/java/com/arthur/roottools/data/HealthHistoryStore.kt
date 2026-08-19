package com.arthur.roottools.data

import android.content.Context
import com.arthur.roottools.model.HealthHistoryPoint
import java.io.File

class HealthHistoryStore(context: Context) {
    private val file = File(context.filesDir, "health-history-24h.tsv")
    private val points = ArrayDeque<HealthHistoryPoint>()

    init {
        points.addAll(readFile())
        prune(System.currentTimeMillis())
    }

    @Synchronized
    fun snapshot(): List<HealthHistoryPoint> = points.toList()

    @Synchronized
    fun appendIfDue(point: HealthHistoryPoint): Boolean {
        val last = points.lastOrNull()
        if (last != null && point.timestampMs - last.timestampMs < PERSIST_INTERVAL_MS) return false
        points.addLast(point)
        prune(point.timestampMs)
        persist()
        return true
    }

    private fun readFile(): List<HealthHistoryPoint> {
        if (!file.isFile) return emptyList()
        return runCatching {
            file.useLines { lines -> lines.mapNotNull(HealthHistoryCodec::decode).toList() }
        }.getOrDefault(emptyList())
    }

    private fun prune(nowMs: Long) {
        val cutoff = nowMs - RETENTION_MS
        while (points.firstOrNull()?.timestampMs?.let { it < cutoff } == true) points.removeFirst()
        while (points.size > MAX_POINTS) points.removeFirst()
    }

    private fun persist() {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            tmp.bufferedWriter().use { writer ->
                points.forEach { point ->
                    writer.appendLine(HealthHistoryCodec.encode(point))
                }
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }.onFailure { tmp.delete() }
    }

    companion object {
        const val PERSIST_INTERVAL_MS = 5 * 60_000L
        const val RETENTION_MS = 24 * 60 * 60_000L
        const val MAX_POINTS = 288
    }
}

object HealthHistoryCodec {
    fun encode(point: HealthHistoryPoint): String = listOf(
        point.timestampMs.toString(),
        point.cpuUsagePercent.toString(),
        point.memoryAvailableKb.toString(),
        point.apTempC?.toString().orEmpty(),
        point.skinTempC?.toString().orEmpty(),
        point.batteryLevel?.toString().orEmpty(),
        point.thermalStatus.toString(),
        point.clusterCurrentKHz.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" },
    ).joinToString("\t")

    fun decode(line: String): HealthHistoryPoint? {
        val parts = line.split('\t')
        if (parts.size < 7) return null
        val clusters = parts.getOrNull(7).orEmpty()
            .split(';')
            .mapNotNull { token ->
                val id = token.substringBefore(':', "").toIntOrNull()
                val khz = token.substringAfter(':', "").toLongOrNull()
                if (id != null && khz != null) id to khz else null
            }
            .toMap()
        return HealthHistoryPoint(
            timestampMs = parts[0].toLongOrNull() ?: return null,
            cpuUsagePercent = parts[1].toFloatOrNull() ?: return null,
            memoryAvailableKb = parts[2].toLongOrNull() ?: return null,
            apTempC = parts[3].toFloatOrNull(),
            skinTempC = parts[4].toFloatOrNull(),
            batteryLevel = parts[5].toIntOrNull(),
            thermalStatus = parts[6].toIntOrNull() ?: return null,
            clusterCurrentKHz = clusters,
        )
    }
}
