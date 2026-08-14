package com.arthur.nettools.intercept

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object InterceptionRuntime {
    private val mutable = MutableStateFlow(InterceptionState())
    val state: StateFlow<InterceptionState> = mutable.asStateFlow()

    fun update(transform: (InterceptionState) -> InterceptionState) {
        mutable.value = transform(mutable.value)
    }

    fun replace(value: InterceptionState) {
        mutable.value = value
    }
}
