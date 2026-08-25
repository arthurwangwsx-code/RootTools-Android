package com.arthur.roottools.core.agent

enum class AgentSessionStatus {
    IDLE,
    RUNNING,
    PAUSED,
    WAITING_USER,
    COMPLETED,
    FAILED,
    STOPPED,
}

enum class AgentOverlayMode {
    HIDDEN,
    COLLAPSED,
    EXPANDED,
}

data class AgentSessionState(
    val taskId: String? = null,
    val title: String = "",
    val targetPackage: String? = null,
    val targetLabel: String? = null,
    val currentStep: String = "",
    val status: AgentSessionStatus = AgentSessionStatus.IDLE,
    val progressCurrent: Int? = null,
    val progressTotal: Int? = null,
    val overlayMode: AgentOverlayMode = AgentOverlayMode.COLLAPSED,
    val startedAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
) {
    val active: Boolean
        get() = status == AgentSessionStatus.RUNNING ||
            status == AgentSessionStatus.PAUSED ||
            status == AgentSessionStatus.WAITING_USER
}

enum class AgentNotificationChannelKind {
    RUNNING,
    ATTENTION,
}
