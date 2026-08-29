package com.arthur.roottools.feature.network.inspection.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthur.roottools.app.rootToolsContainer
import com.arthur.roottools.feature.network.inspection.capture.AppTarget
import com.arthur.roottools.feature.network.inspection.capture.CaptureState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NetworkCaptureUiState(
    val capture: CaptureState = CaptureState(),
    val installedApps: List<AppTarget> = emptyList(),
    val selectedTarget: AppTarget? = null,
    val loadingApps: Boolean = false,
    val runningAction: Boolean = false,
)

class NetworkCaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.rootToolsContainer.networkCaptureRepository
    private val mutableState = MutableStateFlow(NetworkCaptureUiState())
    val state: StateFlow<NetworkCaptureUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.state.collect { capture ->
                mutableState.update { it.copy(capture = capture) }
            }
        }
        refresh()
    }

    fun refresh() {
        if (mutableState.value.runningAction) return
        viewModelScope.launch {
            mutableState.update { it.copy(loadingApps = true, runningAction = true) }
            runCatching {
                repository.initialize()
                repository.installedApps()
            }.onSuccess { apps ->
                mutableState.update { current ->
                    val selection = current.selectedTarget?.let { selected ->
                        apps.firstOrNull { it.packageName == selected.packageName && it.uid == selected.uid }
                    }
                    current.copy(
                        installedApps = apps,
                        selectedTarget = selection,
                        loadingApps = false,
                        runningAction = false,
                    )
                }
            }.onFailure {
                mutableState.update { it.copy(loadingApps = false, runningAction = false) }
            }
        }
    }

    fun selectTarget(target: AppTarget?) {
        if (mutableState.value.capture.active != null || mutableState.value.runningAction) return
        mutableState.update { it.copy(selectedTarget = target) }
    }

    fun startCapture() = runCaptureAction {
        repository.start(mutableState.value.selectedTarget)
    }

    fun stopCapture() = runCaptureAction(repository::stop)

    private fun runCaptureAction(action: suspend () -> Unit) {
        if (mutableState.value.runningAction) return
        viewModelScope.launch {
            mutableState.update { it.copy(runningAction = true) }
            runCatching { action() }
            mutableState.update { it.copy(runningAction = false) }
        }
    }
}
