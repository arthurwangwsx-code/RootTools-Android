package com.arthur.roottools.feature.network.inspection.intercept

import android.content.Context
import com.arthur.roottools.feature.network.inspection.capture.AppTarget
import com.arthur.roottools.feature.network.inspection.intercept.DecryptedEvent
import com.arthur.roottools.feature.network.inspection.intercept.DecryptedKind
import com.arthur.roottools.feature.network.inspection.intercept.InterceptionSession
import org.json.JSONObject
import java.io.File

class InterceptionStore(context: Context) {
    private val root = File(context.getExternalFilesDir(null), "intercepts").apply { mkdirs() }

    fun loadSessions(): List<InterceptionSession> = root.listFiles()
        ?.asSequence()
        ?.filter(File::isDirectory)
        ?.mapNotNull { directory ->
            val summary = File(directory, "session.json")
            if (!summary.exists()) return@mapNotNull null
            runCatching { parseSession(summary, directory) }.getOrNull()
        }
        ?.sortedByDescending(InterceptionSession::startedAt)
        ?.toList()
        ?: emptyList()

    fun findSession(id: String): InterceptionSession? = loadSessions().firstOrNull { it.id == id }

    fun loadEvents(session: InterceptionSession, limit: Int = DEFAULT_EVENT_LIMIT): List<DecryptedEvent> {
        val file = File(session.directory, "events.jsonl")
        if (!file.exists()) return emptyList()
        return file.useLines { lines ->
            lines.take(limit.coerceIn(1, MAX_EVENT_LIMIT)).mapNotNull { line ->
                runCatching { parseEvent(JSONObject(line)) }.getOrNull()
            }.toList()
        }.sortedByDescending(DecryptedEvent::timestamp)
    }

    fun totalSizeBytes(): Long = root.walkTopDown().filter(File::isFile).sumOf(File::length)

    private fun parseEvent(json: JSONObject) = DecryptedEvent(
        id = json.getLong("id"),
        timestamp = json.getLong("timestamp"),
        ipVersion = json.optInt("ipVersion"),
        ipProtocol = json.optInt("ipProtocol"),
        port = json.optInt("port"),
        kind = DecryptedKind.fromWire(json.optString("kind")),
        size = json.optInt("size"),
        title = json.optString("title"),
        preview = json.optString("preview"),
        payloadPath = json.optString("payloadPath").takeIf { it.isNotBlank() && it != "null" },
    )

    private fun parseSession(file: File, directory: File): InterceptionSession {
        val json = JSONObject(file.readText())
        val packageName = json.getString("packageName")
        return InterceptionSession(
            id = json.getString("id"),
            target = AppTarget(
                label = json.optString("appLabel", packageName),
                packageName = packageName,
                uid = json.optInt("uid", -1),
            ),
            startedAt = json.getLong("startedAt"),
            directory = directory.absolutePath,
            stoppedAt = json.optLong("stoppedAt").takeIf { !json.isNull("stoppedAt") },
            decryptedEvents = json.optInt("decryptedEvents"),
            httpRequests = json.optInt("httpRequests"),
            httpResponses = json.optInt("httpResponses"),
            tlsErrors = json.optInt("tlsErrors"),
        )
    }

    companion object {
        private const val DEFAULT_EVENT_LIMIT = 1_000
        private const val MAX_EVENT_LIMIT = 10_000
    }
}
