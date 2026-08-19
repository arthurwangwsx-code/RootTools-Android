package com.arthur.roottools.policy

import android.content.Context
import com.arthur.roottools.model.CpuPolicyEvent
import com.arthur.roottools.model.CpuPolicyEventType
import java.io.File

class CpuPolicyEventStore(context: Context) {
    private val file = File(context.filesDir, "cpu-policy-events.log")

    fun append(type: CpuPolicyEventType, message: String, timestampMs: Long = System.currentTimeMillis()) {
        val safe = message.replace('\n', ' ').replace('|', '/')
        synchronized(lock) {
            val existing = if (file.exists()) file.readLines().takeLast(MAX_EVENTS - 1) else emptyList()
            file.parentFile?.mkdirs()
            file.writeText((existing + "$timestampMs|${type.name}|$safe").joinToString("\n", postfix = "\n"))
        }
    }

    fun read(limit: Int = MAX_EVENTS): List<CpuPolicyEvent> = synchronized(lock) {
        if (!file.exists()) return@synchronized emptyList()
        file.readLines().takeLast(limit.coerceIn(1, MAX_EVENTS)).mapNotNull { line ->
            val parts = line.split('|', limit = 3)
            if (parts.size != 3) return@mapNotNull null
            CpuPolicyEvent(
                timestampMs = parts[0].toLongOrNull() ?: return@mapNotNull null,
                type = runCatching { CpuPolicyEventType.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null,
                message = parts[2],
            )
        }.reversed()
    }

    private companion object {
        val lock = Any()
        const val MAX_EVENTS = 100
    }
}
