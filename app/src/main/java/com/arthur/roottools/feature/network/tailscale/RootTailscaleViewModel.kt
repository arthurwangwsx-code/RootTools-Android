package com.arthur.roottools.feature.network.tailscale

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthur.roottools.app.rootToolsContainer
import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleActionCode
import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleActionResult
import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleDecision
import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleSnapshot
import com.arthur.roottools.feature.network.tailscale.policy.RootTailscalePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RootTailscaleUiState(
    val snapshot: RootTailscaleSnapshot = RootTailscaleSnapshot(),
    val decision: RootTailscaleDecision = RootTailscalePolicy.decide(RootTailscaleSnapshot()),
    val loading: Boolean = false,
    val runningAction: RootTailscaleUiAction? = null,
    val lastResult: RootTailscaleActionResult? = null,
    val pendingAuthUrl: String? = null,
    val technicalError: String? = null,
)

enum class RootTailscaleUiAction {
    REFRESH,
    INSTALL,
    AUTHENTICATE,
    ENABLE_USERSPACE_SERVE,
    ENABLE,
    DISABLE,
    REPAIR,
    BOOT,
    STOP_OFFICIAL_APP,
}

class RootTailscaleViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.rootToolsContainer
    private val repository = container.rootTailscaleRepository
    private val controller = container.rootTailscaleController

    private val _state = MutableStateFlow(RootTailscaleUiState())
    val state: StateFlow<RootTailscaleUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = runAction(RootTailscaleUiAction.REFRESH) {
        RootTailscaleActionResult(
            success = true,
            code = RootTailscaleActionCode.AUTH_ALREADY_COMPLETE,
            snapshot = repository.read(),
        )
    }

    fun installRuntime() = runAction(RootTailscaleUiAction.INSTALL, controller::installOrUpdateRuntime)

    fun beginAuthentication() = runAction(RootTailscaleUiAction.AUTHENTICATE, controller::beginAuthentication)

    fun enableUserspaceServe() = runAction(RootTailscaleUiAction.ENABLE_USERSPACE_SERVE, controller::enableUserspaceServe)

    fun enableRootOverlay() = runAction(RootTailscaleUiAction.ENABLE, controller::enableRootOverlay)

    fun disableRootOverlay() = runAction(RootTailscaleUiAction.DISABLE, controller::disableRootOverlay)

    fun repair() = runAction(RootTailscaleUiAction.REPAIR, controller::repair)

    fun setBootEnabled(enabled: Boolean) = runAction(RootTailscaleUiAction.BOOT) {
        controller.setBootEnabled(enabled)
    }

    fun stopOfficialApp() = runAction(RootTailscaleUiAction.STOP_OFFICIAL_APP, controller::stopOfficialTailscaleApp)

    fun consumeAuthUrl() {
        _state.update { it.copy(pendingAuthUrl = null) }
    }

    fun clearResult() {
        _state.update { it.copy(lastResult = null, technicalError = null) }
    }

    private fun runAction(
        action: RootTailscaleUiAction,
        block: suspend () -> RootTailscaleActionResult,
    ) {
        if (_state.value.runningAction != null) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, runningAction = action, technicalError = null) }
            val outcome = withContext(Dispatchers.IO) { runCatching { block() } }
            _state.update { current ->
                outcome.fold(
                    onSuccess = { result ->
                        val exposeResult = action != RootTailscaleUiAction.REFRESH
                        current.copy(
                            snapshot = result.snapshot,
                            decision = RootTailscalePolicy.decide(result.snapshot),
                            loading = false,
                            runningAction = null,
                            lastResult = if (exposeResult) result else current.lastResult,
                            pendingAuthUrl = result.authUrl ?: result.snapshot.authUrl?.takeIf {
                                action == RootTailscaleUiAction.AUTHENTICATE
                            },
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            loading = false,
                            runningAction = null,
                            technicalError = error.message ?: error.javaClass.simpleName,
                        )
                    },
                )
            }
        }
    }
}
