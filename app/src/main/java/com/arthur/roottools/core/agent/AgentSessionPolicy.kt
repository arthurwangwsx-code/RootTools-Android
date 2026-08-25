package com.arthur.roottools.core.agent

object AgentSessionPolicy {
    const val PREVIEW_REFRESH_INTERVAL_MS = 2_000L

    fun shouldRunForeground(state: AgentSessionState): Boolean = state.active

    fun shouldShowOverlay(state: AgentSessionState, canDrawOverlays: Boolean): Boolean =
        state.active && canDrawOverlays && state.overlayMode != AgentOverlayMode.HIDDEN

    fun shouldRefreshPreview(state: AgentSessionState, canDrawOverlays: Boolean): Boolean =
        shouldShowOverlay(state, canDrawOverlays) &&
            state.overlayMode == AgentOverlayMode.EXPANDED &&
            state.status == AgentSessionStatus.RUNNING

    fun notificationChannel(state: AgentSessionState): AgentNotificationChannelKind =
        if (state.status == AgentSessionStatus.WAITING_USER) {
            AgentNotificationChannelKind.ATTENTION
        } else {
            AgentNotificationChannelKind.RUNNING
        }

    fun normalizedProgress(current: Int?, total: Int?): Pair<Int, Int>? {
        if (current == null || total == null || total <= 0 || current < 0) return null
        return current.coerceAtMost(total) to total
    }

    fun nextOverlayMode(current: AgentOverlayMode): AgentOverlayMode = when (current) {
        AgentOverlayMode.HIDDEN -> AgentOverlayMode.COLLAPSED
        AgentOverlayMode.COLLAPSED -> AgentOverlayMode.EXPANDED
        AgentOverlayMode.EXPANDED -> AgentOverlayMode.COLLAPSED
    }
}
