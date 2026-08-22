package com.arthur.roottools.app.shadow

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthur.roottools.R
import com.arthur.roottools.app.rootToolsContainer
import com.arthur.roottools.feature.shadow.ui.ShadowDisplayUiState
import com.arthur.roottools.model.ShadowDisplayActionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShadowDisplayViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = application.rootToolsContainer.shadowDisplayController
    private val _state = MutableStateFlow(ShadowDisplayUiState())
    val state: StateFlow<ShadowDisplayUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorRes = null, errorDetail = null) }
            val result = withContext(Dispatchers.IO) { controller.status() }
            _state.update { current ->
                result.fold(
                    onSuccess = { current.copy(status = it, loading = false) },
                    onFailure = {
                        current.copy(
                            loading = false,
                            errorRes = R.string.shadow_display_operation_failed,
                            errorDetail = it.message,
                        )
                    },
                )
            }
        }
    }

    fun start(width: String, height: String, densityDpi: String) {
        val w = width.toIntOrNull()
        val h = height.toIntOrNull()
        val dpi = densityDpi.toIntOrNull()
        if (w == null || h == null || dpi == null) {
            showInputError()
            return
        }
        runAction(
            successMessage = R.string.shadow_display_started,
            clearPreview = true,
        ) { controller.start(w, h, dpi) }
    }

    fun stop() {
        runAction(
            successMessage = R.string.shadow_display_stopped,
            clearPreview = true,
        ) { controller.stop() }
    }

    fun launchPackage(packageName: String) {
        runAction(R.string.shadow_display_launched) { controller.launchPackage(packageName) }
    }

    fun tap(x: String, y: String) {
        val safeX = x.toIntOrNull()
        val safeY = y.toIntOrNull()
        if (safeX == null || safeY == null) {
            showInputError()
            return
        }
        runAction(R.string.shadow_display_input_sent) { controller.tap(safeX, safeY) }
    }

    fun swipe(x1: String, y1: String, x2: String, y2: String, durationMs: String) {
        val values = listOf(x1, y1, x2, y2, durationMs).map(String::toIntOrNull)
        if (values.any { it == null }) {
            showInputError()
            return
        }
        runAction(R.string.shadow_display_input_sent) {
            controller.swipe(values[0]!!, values[1]!!, values[2]!!, values[3]!!, values[4]!!)
        }
    }

    fun typeText(text: String) {
        runAction(R.string.shadow_display_input_sent) { controller.typeText(text) }
    }

    fun capturePreview() {
        viewModelScope.launch {
            _state.update {
                it.copy(actionRunning = true, messageRes = null, errorRes = null, errorDetail = null)
            }
            val result = withContext(Dispatchers.IO) { controller.capturePreview() }
            _state.update { current ->
                result.fold(
                    onSuccess = {
                        current.copy(
                            actionRunning = false,
                            previewJpeg = it,
                            messageRes = R.string.shadow_display_preview_updated,
                        )
                    },
                    onFailure = {
                        current.copy(
                            actionRunning = false,
                            errorRes = R.string.shadow_display_operation_failed,
                            errorDetail = it.message,
                        )
                    },
                )
            }
        }
    }

    fun clearFeedback() {
        _state.update { it.copy(messageRes = null, errorRes = null, errorDetail = null) }
    }

    private fun runAction(
        @StringRes successMessage: Int,
        clearPreview: Boolean = false,
        action: suspend () -> ShadowDisplayActionResult,
    ) {
        viewModelScope.launch {
            _state.update {
                it.copy(actionRunning = true, messageRes = null, errorRes = null, errorDetail = null)
            }
            val actionResult = withContext(Dispatchers.IO) { action() }
            val statusResult = withContext(Dispatchers.IO) { controller.status() }
            _state.update { current ->
                val nextStatus = statusResult.getOrNull() ?: current.status
                if (actionResult.success) {
                    current.copy(
                        status = nextStatus,
                        actionRunning = false,
                        lastBackend = actionResult.backend,
                        previewJpeg = if (clearPreview) null else current.previewJpeg,
                        messageRes = successMessage,
                    )
                } else {
                    current.copy(
                        status = nextStatus,
                        actionRunning = false,
                        lastBackend = actionResult.backend,
                        errorRes = R.string.shadow_display_operation_failed,
                        errorDetail = actionResult.detail,
                    )
                }
            }
        }
    }

    private fun showInputError() {
        _state.update {
            it.copy(messageRes = null, errorRes = R.string.shadow_display_invalid_number, errorDetail = null)
        }
    }
}
