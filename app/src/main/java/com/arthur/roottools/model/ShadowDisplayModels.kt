package com.arthur.roottools.model

data class ShadowDisplayConfig(
    val width: Int = 720,
    val height: Int = 1600,
    val densityDpi: Int = 320,
)

enum class ShadowDisplayRuntimeState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR,
}

data class ShadowDisplayStatus(
    val state: ShadowDisplayRuntimeState = ShadowDisplayRuntimeState.STOPPED,
    val displayId: Int? = null,
    val pid: Int? = null,
    val config: ShadowDisplayConfig = ShadowDisplayConfig(),
    val processAlive: Boolean = false,
    val displayActive: Boolean = false,
    val startedAtMs: Long? = null,
    val error: String? = null,
) {
    val running: Boolean
        get() = state == ShadowDisplayRuntimeState.RUNNING && processAlive && displayActive && displayId != null
}

data class ShadowDisplayActionResult(
    val success: Boolean,
    val backend: PrivilegeRouteBackend,
    val detail: String = "",
)
