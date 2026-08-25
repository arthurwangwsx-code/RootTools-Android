package com.arthur.roottools.feature.agent

import android.content.Context
import com.arthur.roottools.R
import com.arthur.roottools.core.agent.AgentOverlayMode
import com.arthur.roottools.core.agent.AgentSessionState
import com.arthur.roottools.core.agent.AgentSessionStatus
import com.arthur.roottools.feature.agent.data.AgentSessionStore
import com.arthur.roottools.feature.agent.service.AgentSessionService
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class AgentSessionManager(
    private val context: Context,
    private val store: AgentSessionStore,
) {
    val state: StateFlow<AgentSessionState> = store.state

    fun start(
        title: String,
        step: String,
        targetPackage: String? = null,
        targetLabel: String? = null,
    ) {
        val now = System.currentTimeMillis()
        store.update {
            AgentSessionState(
                taskId = UUID.randomUUID().toString(),
                title = title.take(120),
                targetPackage = targetPackage,
                targetLabel = targetLabel,
                currentStep = step.take(200),
                status = AgentSessionStatus.RUNNING,
                overlayMode = AgentOverlayMode.COLLAPSED,
                startedAtEpochMs = now,
                updatedAtEpochMs = now,
            )
        }
        AgentSessionService.ensureRunning(context)
    }

    fun ensureShadowSession() {
        if (state.value.active) return
        start(
            title = context.getString(R.string.agent_session_shadow_title),
            step = context.getString(R.string.agent_session_shadow_ready_step),
        )
    }

    fun updateStep(step: String, targetPackage: String? = null, targetLabel: String? = null) {
        val now = System.currentTimeMillis()
        val current = state.value
        if (!current.active) ensureShadowSession()
        store.update {
            it.copy(
                currentStep = step.take(200),
                targetPackage = targetPackage ?: it.targetPackage,
                targetLabel = targetLabel ?: it.targetLabel,
                updatedAtEpochMs = now,
            )
        }
        AgentSessionService.ensureRunning(context)
    }

    fun setProgress(current: Int?, total: Int?) {
        store.update {
            it.copy(progressCurrent = current, progressTotal = total, updatedAtEpochMs = System.currentTimeMillis())
        }
    }

    fun pause() = setStatus(AgentSessionStatus.PAUSED)
    fun resume() = setStatus(AgentSessionStatus.RUNNING)
    fun waitForUser(step: String) {
        updateStep(step)
        setStatus(AgentSessionStatus.WAITING_USER)
    }

    fun stop() {
        setStatus(AgentSessionStatus.STOPPED)
        AgentSessionService.stop(context)
    }

    fun setOverlayMode(mode: AgentOverlayMode) {
        store.update { it.copy(overlayMode = mode, updatedAtEpochMs = System.currentTimeMillis()) }
        if (state.value.active) AgentSessionService.ensureRunning(context)
    }

    fun toggleOverlay() = setOverlayMode(
        when (state.value.overlayMode) {
            AgentOverlayMode.HIDDEN -> AgentOverlayMode.COLLAPSED
            AgentOverlayMode.COLLAPSED -> AgentOverlayMode.EXPANDED
            AgentOverlayMode.EXPANDED -> AgentOverlayMode.COLLAPSED
        }
    )

    private fun setStatus(status: AgentSessionStatus) {
        store.update { it.copy(status = status, updatedAtEpochMs = System.currentTimeMillis()) }
        if (status == AgentSessionStatus.RUNNING || status == AgentSessionStatus.PAUSED || status == AgentSessionStatus.WAITING_USER) {
            AgentSessionService.ensureRunning(context)
        }
    }
}
