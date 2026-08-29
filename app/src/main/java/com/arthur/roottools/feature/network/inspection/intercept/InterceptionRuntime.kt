package com.arthur.roottools.feature.network.inspection.intercept

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object InterceptionRuntime {
    private val mutableState = MutableStateFlow(InterceptionState())
    val state: StateFlow<InterceptionState> = mutableState.asStateFlow()

    fun update(transform: (InterceptionState) -> InterceptionState) {
        mutableState.value = transform(mutableState.value)
    }
}
