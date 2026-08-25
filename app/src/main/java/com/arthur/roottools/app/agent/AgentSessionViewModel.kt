package com.arthur.roottools.app.agent

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthur.roottools.R
import com.arthur.roottools.app.rootToolsContainer
import com.arthur.roottools.core.agent.AgentOverlayMode
import com.arthur.roottools.core.agent.AgentSessionState
import com.arthur.roottools.core.agent.AgentSessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

data class AgentSessionUiState(
    val session: AgentSessionState = AgentSessionState(),
    val canDrawOverlays: Boolean = false,
    val previewJpeg: ByteArray? = null,
    val previewLoading: Boolean = false,
    val previewError: String? = null,
)

class AgentSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val manager = application.rootToolsContainer.agentSessionManager
    private val shadowController = application.rootToolsContainer.createShadowDisplayController("AgentSessionUI")
    private val _state = MutableStateFlow(
        AgentSessionUiState(
            session = manager.state.value,
            canDrawOverlays = Settings.canDrawOverlays(application),
        )
    )
    val state: StateFlow<AgentSessionUiState> = _state.asStateFlow()

    init {
        if (manager.state.value.active) {
            manager.setOverlayMode(manager.state.value.overlayMode)
        }
        viewModelScope.launch {
            manager.state.collect { session -> _state.value = _state.value.copy(session = session) }
        }
    }

    fun refreshPermission() {
        _state.value = _state.value.copy(canDrawOverlays = Settings.canDrawOverlays(app))
    }

    fun startObserverSession() {
        manager.start(
            title = app.getString(R.string.agent_session_shadow_title),
            step = app.getString(R.string.agent_session_observer_started_step),
        )
    }

    fun pauseResume() {
        if (manager.state.value.status == AgentSessionStatus.PAUSED) manager.resume() else manager.pause()
    }

    fun showOverlay(expanded: Boolean = false) {
        manager.setOverlayMode(if (expanded) AgentOverlayMode.EXPANDED else AgentOverlayMode.COLLAPSED)
    }

    fun hideOverlay() = manager.setOverlayMode(AgentOverlayMode.HIDDEN)
    fun stop() = manager.stop()

    fun capturePreview() {
        viewModelScope.launch {
            _state.value = _state.value.copy(previewLoading = true, previewError = null)
            val result = withContext(Dispatchers.IO) { shadowController.capturePreview() }
            _state.value = result.fold(
                onSuccess = { _state.value.copy(previewLoading = false, previewJpeg = it) },
                onFailure = { _state.value.copy(previewLoading = false, previewError = it.message) },
            )
        }
    }
}
