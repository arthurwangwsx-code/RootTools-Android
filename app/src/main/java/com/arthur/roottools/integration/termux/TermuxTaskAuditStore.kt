package com.arthur.roottools.integration.termux

import android.content.Context
import java.io.File

data class TermuxTaskAuditRecord(
    val timestampMs: Long,
    val taskId: TermuxManagedTaskId,
    val mutation: TermuxTaskMutation,
    val success: Boolean,
    val exitCode: Int,
    val durationMs: Long,
    val truncated: Boolean,
)

/**
 * Metadata-only audit for the Linux execution plane.
 *
 * stdout/stderr, raw command text, credentials and stdin are intentionally never persisted here.
 */
class TermuxTaskAuditStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "termux-task-audit.tsv")

    fun record(record: TermuxTaskAuditRecord) = synchronized(lock) {
        val rows = readUnlocked().toMutableList()
        rows += record
        val tmp = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            tmp.bufferedWriter().use { writer ->
                rows.takeLast(MAX_RECORDS).forEach { writer.appendLine(encode(it)) }
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }.onFailure { tmp.delete() }
    }

    fun read(limit: Int = 30): List<TermuxTaskAuditRecord> = synchronized(lock) {
        readUnlocked().takeLast(limit.coerceAtLeast(0)).asReversed()
    }

    private fun readUnlocked(): List<TermuxTaskAuditRecord> {
        if (!file.isFile) return emptyList()
        return runCatching {
            file.useLines { lines -> lines.mapNotNull(::decode).toList() }
        }.getOrDefault(emptyList())
    }

    private fun encode(record: TermuxTaskAuditRecord): String = listOf(
        record.timestampMs,
        record.taskId.name,
        record.mutation.name,
        if (record.success) 1 else 0,
        record.exitCode,
        record.durationMs,
        if (record.truncated) 1 else 0,
    ).joinToString("\t")

    private fun decode(raw: String): TermuxTaskAuditRecord? {
        val parts = raw.split('\t')
        if (parts.size != 7) return null
        return TermuxTaskAuditRecord(
            timestampMs = parts[0].toLongOrNull() ?: return null,
            taskId = runCatching { TermuxManagedTaskId.valueOf(parts[1]) }.getOrNull() ?: return null,
            mutation = runCatching { TermuxTaskMutation.valueOf(parts[2]) }.getOrNull() ?: return null,
            success = parts[3] == "1",
            exitCode = parts[4].toIntOrNull() ?: return null,
            durationMs = parts[5].toLongOrNull() ?: return null,
            truncated = parts[6] == "1",
        )
    }

    companion object {
        private const val MAX_RECORDS = 200
        private val lock = Any()
    }
}

