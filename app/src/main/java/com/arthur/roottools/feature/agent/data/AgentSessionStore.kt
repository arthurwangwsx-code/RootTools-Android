package com.arthur.roottools.feature.agent.data

import android.content.Context
import androidx.core.content.edit
import com.arthur.roottools.core.agent.AgentOverlayMode
import com.arthur.roottools.core.agent.AgentSessionState
import com.arthur.roottools.core.agent.AgentSessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class AgentSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AgentSessionState> = _state.asStateFlow()

    @Synchronized
    fun update(transform: (AgentSessionState) -> AgentSessionState): AgentSessionState {
        val next = transform(_state.value)
        _state.value = next
        write(next)
        return next
    }

    private fun read(): AgentSessionState {
        val raw = prefs.getString(KEY_STATE, null) ?: return AgentSessionState()
        return runCatching {
            val json = JSONObject(raw)
            AgentSessionState(
                taskId = json.optString("taskId").takeIf(String::isNotBlank),
                title = json.optString("title"),
                targetPackage = json.optString("targetPackage").takeIf(String::isNotBlank),
                targetLabel = json.optString("targetLabel").takeIf(String::isNotBlank),
                currentStep = json.optString("currentStep"),
                status = AgentSessionStatus.valueOf(json.optString("status", AgentSessionStatus.IDLE.name)),
                progressCurrent = json.optInt("progressCurrent", -1).takeIf { it >= 0 },
                progressTotal = json.optInt("progressTotal", -1).takeIf { it > 0 },
                overlayMode = AgentOverlayMode.valueOf(json.optString("overlayMode", AgentOverlayMode.COLLAPSED.name)),
                startedAtEpochMs = json.optLong("startedAtEpochMs", 0L),
                updatedAtEpochMs = json.optLong("updatedAtEpochMs", 0L),
            )
        }.getOrDefault(AgentSessionState())
    }

    private fun write(state: AgentSessionState) {
        val json = JSONObject()
            .put("taskId", state.taskId.orEmpty())
            .put("title", state.title)
            .put("targetPackage", state.targetPackage.orEmpty())
            .put("targetLabel", state.targetLabel.orEmpty())
            .put("currentStep", state.currentStep)
            .put("status", state.status.name)
            .put("progressCurrent", state.progressCurrent ?: -1)
            .put("progressTotal", state.progressTotal ?: -1)
            .put("overlayMode", state.overlayMode.name)
            .put("startedAtEpochMs", state.startedAtEpochMs)
            .put("updatedAtEpochMs", state.updatedAtEpochMs)
        prefs.edit { putString(KEY_STATE, json.toString()) }
    }

    private companion object {
        const val PREFS = "agent_session_state"
        const val KEY_STATE = "state"
    }
}
