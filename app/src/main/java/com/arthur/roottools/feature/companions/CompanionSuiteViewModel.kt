package com.arthur.roottools.feature.companions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CompanionSuiteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CompanionSuiteRepository(application)
    private val mutableState = MutableStateFlow(CompanionSuiteUiState())
    val state: StateFlow<CompanionSuiteUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        mutableState.value = mutableState.value.copy(loading = true)
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = CompanionSuiteUiState(
                loading = false,
                tools = repository.snapshot(),
            )
        }
    }
}
