package com.arthur.roottools.data

import android.content.Context
import com.arthur.roottools.model.RootActionAuditRecord
import java.io.File

class RootActionAuditStore(context: Context) {
    private val file = File(context.filesDir, "root-action-audit.tsv")

    fun record(
        source: String,
        feature: String,
        action: String,
        target: String = "",
        before: String = "",
        after: String = "",
        success: Boolean,
        rollbackHint: String = "",
    ) = synchronized(lock) {
        val current = readUnlocked().toMutableList()
        current += RootActionAuditRecord(
            timestampMs = System.currentTimeMillis(),
            source = source,
            feature = feature,
            action = action,
            target = target,
            before = before,
            after = after,
            success = success,
            rollbackHint = rollbackHint,
        )
        val trimmed = current.takeLast(MAX_RECORDS)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            tmp.bufferedWriter().use { writer ->
                trimmed.forEach { writer.appendLine(encode(it)) }
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }.onFailure { tmp.delete() }
    }

    fun read(limit: Int = 50): List<RootActionAuditRecord> = synchronized(lock) {
        readUnlocked().takeLast(limit.coerceAtLeast(0)).asReversed()
    }

    private fun readUnlocked(): List<RootActionAuditRecord> {
        if (!file.isFile) return emptyList()
        return runCatching {
            file.useLines { lines -> lines.mapNotNull(::decode).toList() }
        }.getOrDefault(emptyList())
    }

    private fun encode(record: RootActionAuditRecord): String = listOf(
        record.timestampMs.toString(),
        record.source,
        record.feature,
        record.action,
        record.target,
        record.before,
        record.after,
        if (record.success) "1" else "0",
        record.rollbackHint,
    ).joinToString("\t") { sanitize(it) }

    private fun decode(line: String): RootActionAuditRecord? {
        val parts = line.split('\t')
        if (parts.size < 9) return null
        return RootActionAuditRecord(
            timestampMs = parts[0].toLongOrNull() ?: return null,
            source = parts[1],
            feature = parts[2],
            action = parts[3],
            target = parts[4],
            before = parts[5],
            after = parts[6],
            success = parts[7] == "1",
            rollbackHint = parts[8],
        )
    }

    private fun sanitize(value: String): String = value.replace('\t', ' ').replace('\n', ' ').take(MAX_FIELD_LENGTH)

    companion object {
        private val lock = Any()
        private const val MAX_RECORDS = 200
        private const val MAX_FIELD_LENGTH = 280
        internal const val MAX_RECORDS_FOR_TEST = MAX_RECORDS
    }
}
