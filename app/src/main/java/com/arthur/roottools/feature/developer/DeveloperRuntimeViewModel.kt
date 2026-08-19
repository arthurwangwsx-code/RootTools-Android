package com.arthur.roottools.feature.developer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthur.roottools.automation.AutomationClientRecord
import com.arthur.roottools.automation.AutomationClientStore
import com.arthur.roottools.data.TermuxRuntimeRepository
import com.arthur.roottools.integration.termux.OfficialTermuxRunCommandBackend
import com.arthur.roottools.integration.termux.TermuxCliProvisioner
import com.arthur.roottools.integration.termux.TermuxManagedTaskId
import com.arthur.roottools.integration.termux.TermuxRuntimeProbeParser
import com.arthur.roottools.integration.termux.TermuxTaskResult
import com.arthur.roottools.model.TermuxBridgeMode
import com.arthur.roottools.model.TermuxRuntimeSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeveloperRuntimeUiState(
    val runtime: TermuxRuntimeSnapshot = TermuxRuntimeSnapshot(),
    val loading: Boolean = false,
    val runningTask: TermuxManagedTaskId? = null,
    val lastTaskResult: TermuxTaskResult? = null,
    val termuxClient: AutomationClientRecord? = null,
    val cliArtifactPath: String? = null,
    val message: String? = null,
    val error: String? = null,
)

class DeveloperRuntimeViewModel(application: Application) : AndroidViewModel(application) {
    private val runtimeRepository = TermuxRuntimeRepository(application)
    private val clientStore = AutomationClientStore(application)
    private val cliProvisioner = TermuxCliProvisioner(application)
    private val runCommandBackend = OfficialTermuxRunCommandBackend(application)

    private val _state = MutableStateFlow(DeveloperRuntimeUiState())
    val state: StateFlow<DeveloperRuntimeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = withContext(Dispatchers.IO) { runCatching { runtimeRepository.read() } }
            _state.update { current ->
                result.fold(
                    onSuccess = { snapshot ->
                        current.copy(
                            runtime = snapshot,
                            loading = false,
                            termuxClient = currentTermuxClient(),
                            cliArtifactPath = cliProvisioner.existingArtifact()?.absolutePath,
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            loading = false,
                            error = error.message ?: "Unable to read Termux runtime",
                        )
                    },
                )
            }
        }
    }

    fun provisionCli() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, message = null) }
            val result = withContext(Dispatchers.IO) { runCatching { cliProvisioner.provision() } }
            _state.update { current ->
                result.fold(
                    onSuccess = { artifact ->
                        current.copy(
                            loading = false,
                            termuxClient = currentTermuxClient(),
                            cliArtifactPath = artifact.file.absolutePath,
                            message = "Termux CLI credential rotated and export file created",
                        )
                    },
                    onFailure = { error -> current.copy(loading = false, error = error.message ?: "CLI provisioning failed") },
                )
            }
        }
    }

    fun revokeCli() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { cliProvisioner.revoke() }
            _state.update {
                it.copy(
                    termuxClient = currentTermuxClient(),
                    cliArtifactPath = null,
                    message = "Termux CLI credential revoked",
                    error = null,
                )
            }
        }
    }

    fun runManagedTask(taskId: TermuxManagedTaskId) {
        if (_state.value.runtime.bridgeMode != TermuxBridgeMode.OFFICIAL_RUN_COMMAND) {
            _state.update { it.copy(error = "Official Termux RUN_COMMAND bridge is not ready") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(runningTask = taskId, lastTaskResult = null, error = null, message = null) }
            val result = withContext(Dispatchers.IO) { runCommandBackend.execute(taskId) }
            _state.update { current ->
                val updatedRuntime = if (taskId == TermuxManagedTaskId.RUNTIME_PROBE && result.success) {
                    val tools = TermuxRuntimeProbeParser.parse(result.stdout)
                    current.runtime.copy(
                        git = tools.git,
                        python = tools.python,
                        node = tools.node,
                        sshd = tools.sshd,
                    )
                } else {
                    current.runtime
                }
                current.copy(
                    runtime = updatedRuntime,
                    runningTask = null,
                    lastTaskResult = result,
                    error = if (result.success) null else result.transportError
                        ?: result.internalErrorMessage.takeIf(String::isNotBlank)
                        ?: result.stderr.takeIf(String::isNotBlank)
                        ?: "Termux task failed",
                )
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, error = null) }
    }

    private fun currentTermuxClient(): AutomationClientRecord? = clientStore.listClients()
        .firstOrNull { it.clientId == AutomationClientStore.TERMUX_CLIENT_ID && !it.revoked }
}

