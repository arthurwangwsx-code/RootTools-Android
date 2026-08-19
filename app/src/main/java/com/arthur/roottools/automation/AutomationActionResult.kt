package com.arthur.roottools.automation

import org.json.JSONObject

data class AutomationActionResult(
    val requestId: String,
    val command: String,
    val success: Boolean,
    val message: String,
    val backend: String? = null,
    val payload: JSONObject? = null,
) {
    fun toJson(): String = JSONObject()
        .put("requestId", requestId)
        .put("action", command)
        .put("success", success)
        .put("message", message)
        .apply {
            backend?.let { put("backend", it) }
            payload?.let { put("payload", it) }
        }
        .toString()
}

