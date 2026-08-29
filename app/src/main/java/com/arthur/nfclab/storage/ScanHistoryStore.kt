package com.arthur.nfclab.storage

import android.content.Context
import androidx.core.content.edit
import com.arthur.nfclab.nfc.TagSnapshot
import com.arthur.nfclab.nfc.NxpIsoDepProductInspector
import org.json.JSONArray
import org.json.JSONObject

class ScanHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("scan_history", Context.MODE_PRIVATE)

    @Synchronized
    fun save(snapshot: TagSnapshot) {
        val current = load().toMutableList()
        current.add(0, snapshot)
        persist(current.take(MAX_ITEMS))
    }

    @Synchronized
    fun load(): List<TagSnapshot> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(fromJson(array.getJSONObject(index)))
                }
            }
        }.getOrElse { emptyList() }
    }

    @Synchronized
    fun clear() {
        prefs.edit { remove(KEY) }
    }

    private fun persist(items: List<TagSnapshot>) {
        val array = JSONArray()
        items.forEach { array.put(toJson(it)) }
        prefs.edit { putString(KEY, array.toString()) }
    }

    private fun toJson(snapshot: TagSnapshot): JSONObject = JSONObject().apply {
        put("timestamp", snapshot.timestampMs)
        put("id", snapshot.idHex)
        put("technologies", JSONArray(snapshot.technologies))
        put("records", JSONArray(snapshot.ndefRecords))
        put("warning", snapshot.warning)
        put("details", JSONObject(snapshot.details))
    }

    private fun fromJson(obj: JSONObject): TagSnapshot {
        val technologies = obj.optJSONArray("technologies").toStringList()
        val records = obj.optJSONArray("records").toStringList()
        val detailsObject = obj.optJSONObject("details") ?: JSONObject()
        val details = linkedMapOf<String, String>()
        detailsObject.keys().forEach { key -> details[key] = detailsObject.optString(key) }
        return NxpIsoDepProductInspector.enrichSnapshot(TagSnapshot(
            timestampMs = obj.optLong("timestamp"),
            idHex = obj.optString("id", "-"),
            technologies = technologies,
            details = details,
            ndefRecords = records,
            warning = obj.optString("warning").takeIf { it.isNotBlank() && it != "null" },
        ))
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) add(optString(index))
        }
    }

    companion object {
        private const val KEY = "items"
        private const val MAX_ITEMS = 30
    }
}

