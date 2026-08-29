package com.arthur.nfclab.domain

import org.json.JSONArray
import org.json.JSONObject

enum class ProvisioningRoute {
    OEM_WALLET,
    PARTNER_TSM,
    DIRECT_ESE,
}

enum class ProvisioningReadiness {
    READY,
    MANAGED_EXTERNALLY,
    PARTNER_REQUIRED,
    PRIVILEGED_ONLY,
    BLOCKED,
    UNKNOWN,
}

enum class ProvisioningRequirementState {
    SATISFIED,
    ACTION_AVAILABLE,
    PARTNER_REQUIRED,
    PRIVILEGED_ONLY,
    MISSING,
    UNKNOWN,
}

data class ProvisioningRequirement(
    val id: String,
    val title: String,
    val state: ProvisioningRequirementState,
    val detail: String,
    val actionProviderId: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("state", state.name)
        .put("detail", detail)
        .put("actionProviderId", actionProviderId ?: JSONObject.NULL)
}

data class ProvisioningRouteStatus(
    val route: ProvisioningRoute,
    val readiness: ProvisioningReadiness,
    val title: String,
    val detail: String,
    val providerId: String? = null,
    val requirements: List<ProvisioningRequirement> = emptyList(),
    val evidence: List<String> = emptyList(),
) {
    val unresolvedRequirements: List<ProvisioningRequirement>
        get() = requirements.filterNot {
            it.state == ProvisioningRequirementState.SATISFIED ||
                it.state == ProvisioningRequirementState.ACTION_AVAILABLE
        }

    fun toJson(): JSONObject = JSONObject()
        .put("route", route.name)
        .put("readiness", readiness.name)
        .put("title", title)
        .put("detail", detail)
        .put("providerId", providerId ?: JSONObject.NULL)
        .put("requirements", JSONArray().apply { requirements.forEach { put(it.toJson()) } })
        .put("evidence", JSONArray(evidence))
}

data class ProvisioningCapabilityReport(
    val routes: List<ProvisioningRouteStatus>,
    val nextSteps: List<String>,
    val collectedAtMs: Long,
) {
    fun route(route: ProvisioningRoute): ProvisioningRouteStatus? = routes.firstOrNull { it.route == route }

    fun toJson(): JSONObject = JSONObject()
        .put("routes", JSONArray().apply { routes.forEach { put(it.toJson()) } })
        .put("nextSteps", JSONArray(nextSteps))
        .put("collectedAtMs", collectedAtMs)
}
