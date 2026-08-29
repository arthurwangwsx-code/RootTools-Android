package com.arthur.nfclab.domain

import org.json.JSONArray
import org.json.JSONObject

enum class AccessReaderOutcome {
    OPENED,
    REACTED_BUT_FAILED,
    NO_REACTION,
}

enum class AccessDiagnosticConclusion {
    SUCCESS,
    NO_RF_FIELD,
    RF_FIELD_NO_CARD_INTERACTION,
    CARD_INTERACTION_AUTH_FAILED,
    CARD_INTERACTION_NO_READER_FEEDBACK,
    INCONCLUSIVE,
}

data class AccessDiagnosticSignals(
    val rfFieldSeen: Boolean,
    val fieldSessionCount: Int,
    val nfceeActionCount: Int,
    val cardActivationCount: Int,
    val highestProtocolLayer: Int,
    val detectedTechnologies: Set<String>,
    val nfceeAidObserved: Boolean,
    val hciAidObserved: Boolean,
    val activeCardMatched: Boolean?,
    val readerFingerprintObserved: Boolean,
) {
    val cardInteractionSeen: Boolean
        get() = nfceeActionCount > 0 || cardActivationCount > 0 || highestProtocolLayer >= 2

    fun toJson(): JSONObject = JSONObject().apply {
        put("rfFieldSeen", rfFieldSeen)
        put("fieldSessionCount", fieldSessionCount)
        put("nfceeActionCount", nfceeActionCount)
        put("cardActivationCount", cardActivationCount)
        put("highestProtocolLayer", highestProtocolLayer)
        put("detectedTechnologies", JSONArray(detectedTechnologies.sorted()))
        put("nfceeAidObserved", nfceeAidObserved)
        put("hciAidObserved", hciAidObserved)
        put("activeCardMatched", activeCardMatched ?: JSONObject.NULL)
        put("readerFingerprintObserved", readerFingerprintObserved)
    }

    companion object {
        fun fromJson(json: JSONObject): AccessDiagnosticSignals {
            val technologies = buildSet {
                val array = json.optJSONArray("detectedTechnologies") ?: JSONArray()
                for (index in 0 until array.length()) add(array.optString(index))
            }
            return AccessDiagnosticSignals(
                rfFieldSeen = json.optBoolean("rfFieldSeen"),
                fieldSessionCount = json.optInt("fieldSessionCount"),
                nfceeActionCount = json.optInt("nfceeActionCount"),
                cardActivationCount = json.optInt("cardActivationCount"),
                highestProtocolLayer = json.optInt("highestProtocolLayer"),
                detectedTechnologies = technologies,
                nfceeAidObserved = json.optBoolean("nfceeAidObserved"),
                hciAidObserved = json.optBoolean("hciAidObserved"),
                activeCardMatched = if (json.isNull("activeCardMatched")) null else json.optBoolean("activeCardMatched"),
                readerFingerprintObserved = json.optBoolean("readerFingerprintObserved"),
            )
        }
    }
}

data class AccessDiagnosticReport(
    val sessionId: String,
    val providerId: String,
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val cardTitle: String?,
    val cardSourceLabel: String?,
    val outcome: AccessReaderOutcome,
    val conclusion: AccessDiagnosticConclusion,
    val summary: String,
    val evidence: List<String>,
    val recommendations: List<String>,
    val signals: AccessDiagnosticSignals,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sessionId", sessionId)
        put("providerId", providerId)
        put("startedAtMs", startedAtMs)
        put("finishedAtMs", finishedAtMs)
        put("cardTitle", cardTitle ?: JSONObject.NULL)
        put("cardSourceLabel", cardSourceLabel ?: JSONObject.NULL)
        put("outcome", outcome.name)
        put("conclusion", conclusion.name)
        put("summary", summary)
        put("evidence", JSONArray(evidence))
        put("recommendations", JSONArray(recommendations))
        put("signals", signals.toJson())
    }

    companion object {
        fun fromJson(json: JSONObject): AccessDiagnosticReport = AccessDiagnosticReport(
            sessionId = json.optString("sessionId"),
            providerId = json.optString("providerId"),
            startedAtMs = json.optLong("startedAtMs"),
            finishedAtMs = json.optLong("finishedAtMs"),
            cardTitle = json.optString("cardTitle").takeIf { it.isNotBlank() && it != "null" },
            cardSourceLabel = json.optString("cardSourceLabel").takeIf { it.isNotBlank() && it != "null" },
            outcome = runCatching { AccessReaderOutcome.valueOf(json.optString("outcome")) }
                .getOrDefault(AccessReaderOutcome.NO_REACTION),
            conclusion = runCatching { AccessDiagnosticConclusion.valueOf(json.optString("conclusion")) }
                .getOrDefault(AccessDiagnosticConclusion.INCONCLUSIVE),
            summary = json.optString("summary"),
            evidence = json.optJSONArray("evidence").toStringList(),
            recommendations = json.optJSONArray("recommendations").toStringList(),
            signals = AccessDiagnosticSignals.fromJson(json.optJSONObject("signals") ?: JSONObject()),
        )

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return buildList {
                for (index in 0 until length()) add(optString(index))
            }
        }
    }
}
