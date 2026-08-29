package com.arthur.nettools.intercept

import android.content.Context
import com.arthur.nettools.capture.AppTarget
import org.json.JSONObject
import java.io.File

class InterceptionStore(context: Context) {
    private val root = File(context.getExternalFilesDir(null), "intercepts").apply { mkdirs() }

    fun loadSessions(): List<InterceptionSession> = root.listFiles()
        ?.asSequence()
        ?.filter { it.isDirectory }
        ?.mapNotNull { dir ->
            val summary = File(dir, "session.json")
            if (!summary.exists()) return@mapNotNull null
            runCatching { parseSession(summary, dir) }.getOrNull()
        }
        ?.sortedByDescending { it.startedAt }
        ?.toList()
        ?: emptyList()

    fun findSession(id: String): InterceptionSession? = loadSessions().firstOrNull { it.id == id }

    fun loadEvents(session: InterceptionSession, limit: Int = 1_000): List<DecryptedEvent> {
        val file = File(session.directory, "events.jsonl")
        if (!file.exists()) return emptyList()
        val lines = file.useLines { sequence -> sequence.take(limit).toList() }
        return lines.mapNotNull { line ->
            runCatching {
                val j = JSONObject(line)
                DecryptedEvent(
                    id = j.getLong("id"),
                    timestamp = j.getLong("timestamp"),
                    ipVersion = j.optInt("ipVersion"),
                    ipProtocol = j.optInt("ipProtocol"),
                    port = j.optInt("port"),
                    kind = DecryptedKind.fromWire(j.optString("kind")),
                    size = j.optInt("size"),
                    title = j.optString("title"),
                    preview = j.optString("preview"),
                    payloadPath = j.optString("payloadPath").takeIf { it.isNotBlank() && it != "null" },
                )
            }.getOrNull()
        }.sortedByDescending { it.timestamp }
    }

    fun totalSizeBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun parseSession(file: File, dir: File): InterceptionSession {
        val j = JSONObject(file.readText())
        val packageName = j.getString("packageName")
        val target = AppTarget(
            label = j.optString("appLabel", packageName),
            packageName = packageName,
            uid = j.optInt("uid", -1),
        )
        return InterceptionSession(
            id = j.getString("id"),
            target = target,
            startedAt = j.getLong("startedAt"),
            directory = dir.absolutePath,
            stoppedAt = j.optLong("stoppedAt").takeIf { !j.isNull("stoppedAt") },
            decryptedEvents = j.optInt("decryptedEvents"),
            httpRequests = j.optInt("httpRequests"),
            httpResponses = j.optInt("httpResponses"),
            tlsErrors = j.optInt("tlsErrors"),
        )
    }
}
