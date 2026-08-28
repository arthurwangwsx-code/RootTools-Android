package com.arthur.roottools.app.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthur.roottools.app.rootToolsContainer
import com.arthur.roottools.feature.assistant.model.AssistantSwitchResult
import com.arthur.roottools.feature.assistant.model.AssistantSwitchStatus
import com.arthur.roottools.feature.assistant.ui.AssistantSettingsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AssistantSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.rootToolsContainer.assistantRepository
    private val controller = application.rootToolsContainer.assistantController
    private val mutableState = MutableStateFlow(AssistantSettingsUiState())
    val state: StateFlow<AssistantSettingsUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (mutableState.value.switchingPackage != null) return
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, loadFailure = null) }
            runCatching { repository.snapshot() }.fold(
                onSuccess = { snapshot ->
                    mutableState.update { it.copy(loading = false, snapshot = snapshot, loadFailure = null) }
                },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(
                            loading = false,
                            loadFailure = error.message ?: error.javaClass.simpleName,
                        )
                    }
                },
            )
        }
    }

    fun switchTo(packageName: String) {
        if (mutableState.value.switchingPackage != null) return
        viewModelScope.launch {
            mutableState.update { it.copy(switchingPackage = packageName, feedback = null) }
            val result = runCatching { controller.switchTo(packageName) }.getOrElse { error ->
                AssistantSwitchResult(
                    status = AssistantSwitchStatus.WRITE_FAILED,
                    previousPackage = mutableState.value.snapshot.currentPackage,
                    currentPackage = mutableState.value.snapshot.currentPackage,
                    detail = error.message ?: error.javaClass.simpleName,
                )
            }
            val refreshed = runCatching { repository.snapshot() }.getOrNull()
            mutableState.update {
                it.copy(
                    loading = false,
                    switchingPackage = null,
                    snapshot = refreshed ?: it.snapshot,
                    feedback = result,
                    loadFailure = if (refreshed == null) result.detail.takeIf(String::isNotBlank) else null,
                )
            }
        }
    }

    fun dismissFeedback() {
        mutableState.update { it.copy(feedback = null) }
    }
}
