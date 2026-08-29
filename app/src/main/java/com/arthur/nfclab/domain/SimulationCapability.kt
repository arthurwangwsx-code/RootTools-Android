package com.arthur.nfclab.domain

import org.json.JSONArray
import org.json.JSONObject

enum class SimulationLayer {
    RF_IDENTITY,
    ISO_DEP_TRANSPORT,
    APPLICATION_PROTOCOL,
    SECURE_CREDENTIAL,
    OFF_HOST_SE,
}

enum class SimulationSupport {
    SUPPORTED,
    PARTIAL,
    REQUIRES_PROVISIONING,
    UNSUPPORTED,
    UNKNOWN,
}

enum class SimulationRoute {
    HOST_HCE,
    OEM_OFF_HOST,
    CUSTOM_ESE_APPLET,
}

data class SimulationRouteStatus(
    val route: SimulationRoute,
    val support: SimulationSupport,
    val title: String,
    val detail: String,
    val sourceLabel: String? = null,
    val cardTitles: List<String> = emptyList(),
    val managementProviderId: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("route", route.name)
        .put("support", support.name)
        .put("title", title)
        .put("detail", detail)
        .put("sourceLabel", sourceLabel ?: JSONObject.NULL)
        .put("cardTitles", JSONArray(cardTitles))
        .put("managementProviderId", managementProviderId ?: JSONObject.NULL)
}

data class SimulationLayerStatus(
    val layer: SimulationLayer,
    val support: SimulationSupport,
    val title: String,
    val detail: String,
    val evidence: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("layer", layer.name)
        .put("support", support.name)
        .put("title", title)
        .put("detail", detail)
        .put("evidence", JSONArray(evidence))
}

data class SimulationCapabilityReport(
    val targetProduct: String?,
    val targetTechnology: String,
    val routes: List<SimulationRouteStatus>,
    val layers: List<SimulationLayerStatus>,
    val recommendedPath: List<String>,
    val blockers: List<String>,
    val collectedAtMs: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("targetProduct", targetProduct ?: JSONObject.NULL)
        .put("targetTechnology", targetTechnology)
        .put("routes", JSONArray().apply { routes.forEach { put(it.toJson()) } })
        .put("layers", JSONArray().apply { layers.forEach { put(it.toJson()) } })
        .put("recommendedPath", JSONArray(recommendedPath))
        .put("blockers", JSONArray(blockers))
        .put("collectedAtMs", collectedAtMs)
}
