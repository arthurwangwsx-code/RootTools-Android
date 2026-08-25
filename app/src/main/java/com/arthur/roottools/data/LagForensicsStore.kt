package com.arthur.roottools.data

import android.content.Context
import com.arthur.roottools.model.LagForensicsSample
import com.arthur.roottools.model.LagIncidentSummary
import com.arthur.roottools.model.LagPressureLevel
import java.io.File

class LagForensicsStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val directory = File(appContext.filesDir, DIRECTORY_NAME)

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    fun writeIncident(
        summary: LagIncidentSummary,
        history: List<LagForensicsSample>,
        evidence: String,
    ): LagIncidentSummary {
        directory.mkdirs()
        val file = File(directory, summary.evidenceFileName)
        val body = buildString {
            appendLine("capturedAtMs=${summary.capturedAtMs}")
            appendLine("level=${summary.level.name}")
            appendLine("reason=${summary.reason.replace('\n', ' ')}")
            appendLine("memorySome10=${summary.memorySome10}")
            appendLine("memoryFull10=${summary.memoryFull10}")
            appendLine("ioSome10=${summary.ioSome10}")
            appendLine("ioFull10=${summary.ioFull10}")
            appendLine("cpuSome10=${summary.cpuSome10}")
            appendLine("memAvailableRatio=${summary.memAvailableRatio}")
            appendLine("--- HISTORY ---")
            history.forEach { sample ->
                appendLine(
                    listOf(
                        sample.timestampMs,
                        sample.level.name,
                        sample.memorySome10,
                        sample.memoryFull10,
                        sample.ioSome10,
                        sample.ioFull10,
                        sample.cpuSome10,
                        sample.memAvailableRatio,
                        sample.swapUsedKb,
                        sample.thermalStatus,
                        sample.skinTempC ?: "",
                    ).joinToString("|"),
                )
            }
            appendLine("--- EVIDENCE ---")
            append(evidence)
        }.take(MAX_INCIDENT_CHARS)
        file.writeText(body)
        prune()
        return summary
    }

    fun readSummaries(limit: Int = MAX_INCIDENT_FILES): List<LagIncidentSummary> {
        if (!directory.exists()) return emptyList()
        return directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(FILE_PREFIX) && it.name.endsWith(".txt") }
            .sortedByDescending { it.lastModified() }
            .take(limit)
            .mapNotNull(::parseSummary)
    }

    fun readLatestEvidence(): String {
        val file = directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(FILE_PREFIX) && it.name.endsWith(".txt") }
            .maxByOrNull { it.lastModified() }
            ?: return ""
        return runCatching { file.readText().take(MAX_INCIDENT_CHARS) }.getOrDefault("")
    }

    private fun parseSummary(file: File): LagIncidentSummary? {
        val values = linkedMapOf<String, String>()
        runCatching {
            file.useLines { lines ->
                lines.takeWhile { it != "--- HISTORY ---" }.forEach { line ->
                    val key = line.substringBefore('=', "")
                    if (key.isNotBlank()) values[key] = line.substringAfter('=', "")
                }
            }
        }.getOrElse { return null }
        val capturedAtMs = values["capturedAtMs"]?.toLongOrNull() ?: return null
        return LagIncidentSummary(
            capturedAtMs = capturedAtMs,
            level = runCatching { LagPressureLevel.valueOf(values["level"].orEmpty()) }.getOrDefault(LagPressureLevel.ELEVATED),
            reason = values["reason"].orEmpty(),
            memorySome10 = values["memorySome10"]?.toFloatOrNull() ?: 0f,
            memoryFull10 = values["memoryFull10"]?.toFloatOrNull() ?: 0f,
            ioSome10 = values["ioSome10"]?.toFloatOrNull() ?: 0f,
            ioFull10 = values["ioFull10"]?.toFloatOrNull() ?: 0f,
            cpuSome10 = values["cpuSome10"]?.toFloatOrNull() ?: 0f,
            memAvailableRatio = values["memAvailableRatio"]?.toFloatOrNull() ?: 0f,
            evidenceFileName = file.name,
        )
    }

    private fun prune() {
        directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(FILE_PREFIX) && it.name.endsWith(".txt") }
            .sortedByDescending { it.lastModified() }
            .drop(MAX_INCIDENT_FILES)
            .forEach { runCatching { it.delete() } }
    }

    companion object {
        private const val PREFS_NAME = "lag_forensics"
        private const val KEY_ENABLED = "enabled"
        private const val DIRECTORY_NAME = "lag_forensics"
        const val FILE_PREFIX = "lag_incident_"
        private const val MAX_INCIDENT_FILES = 5
        private const val MAX_INCIDENT_CHARS = 128_000
    }
}
