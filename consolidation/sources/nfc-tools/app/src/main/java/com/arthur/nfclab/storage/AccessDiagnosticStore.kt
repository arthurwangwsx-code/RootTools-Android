package com.arthur.nfclab.storage

import android.content.Context
import androidx.core.content.edit
import com.arthur.nfclab.domain.AccessDiagnosticReport
import org.json.JSONArray

class AccessDiagnosticStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<AccessDiagnosticReport> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(AccessDiagnosticReport.fromJson(array.getJSONObject(index)))
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(report: AccessDiagnosticReport) {
        val items = buildList {
            add(report)
            addAll(load().filterNot { it.sessionId == report.sessionId })
        }.take(MAX_HISTORY)
        prefs.edit {
            putString(
                KEY_HISTORY,
                JSONArray().apply { items.forEach { put(it.toJson()) } }.toString(),
            )
        }
    }

    @Synchronized
    fun clear() {
        prefs.edit { remove(KEY_HISTORY) }
    }

    companion object {
        private const val PREFS = "access_diagnostic_history"
        private const val KEY_HISTORY = "items"
        private const val MAX_HISTORY = 10
    }
}
