package com.arthur.roottools.app.adgovernance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthur.roottools.app.rootToolsContainer
import com.arthur.roottools.feature.adgovernance.ui.AdGovernanceUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdGovernanceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.rootToolsContainer.adGovernanceRepository
    private val _state = MutableStateFlow(AdGovernanceUiState())
    val state: StateFlow<AdGovernanceUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val snapshot = withContext(Dispatchers.IO) { repository.read() }
            _state.update {
                it.copy(
                    loading = false,
                    snapshot = snapshot,
                    error = snapshot.probeError,
                )
            }
        }
    }
}
