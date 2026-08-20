package com.arthur.roottools.workflow

import android.content.Context
import java.io.File

data class ManagedWorkflowAuditRecord(
    val timestampMs: Long,
    val workflowId: ManagedWorkflowId,
    val success: Boolean,
    val completedSteps: Int,
    val durationMs: Long,
)

class ManagedWorkflowAuditStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "managed-workflow-audit.tsv")

    fun record(record: ManagedWorkflowAuditRecord) = synchronized(lock) {
        val rows = readUnlocked().toMutableList().apply { add(record) }.takeLast(MAX_RECORDS)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            tmp.bufferedWriter().use { writer ->
                rows.forEach { row ->
                    writer.appendLine(
                        listOf(
                            row.timestampMs,
                            row.workflowId.name,
                            if (row.success) 1 else 0,
                            row.completedSteps,
                            row.durationMs,
                        ).joinToString("\t")
                    )
                }
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }.onFailure { tmp.delete() }
    }

    fun read(limit: Int = 20): List<ManagedWorkflowAuditRecord> = synchronized(lock) {
        readUnlocked().takeLast(limit.coerceAtLeast(0)).asReversed()
    }

    private fun readUnlocked(): List<ManagedWorkflowAuditRecord> {
        if (!file.isFile) return emptyList()
        return runCatching {
            file.useLines { lines ->
                lines.mapNotNull { raw ->
                    val parts = raw.split('\t')
                    if (parts.size != 5) return@mapNotNull null
                    ManagedWorkflowAuditRecord(
                        timestampMs = parts[0].toLongOrNull() ?: return@mapNotNull null,
                        workflowId = runCatching { ManagedWorkflowId.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null,
                        success = parts[2] == "1",
                        completedSteps = parts[3].toIntOrNull() ?: return@mapNotNull null,
                        durationMs = parts[4].toLongOrNull() ?: return@mapNotNull null,
                    )
                }.toList()
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val MAX_RECORDS = 100
        private val lock = Any()
    }
}

