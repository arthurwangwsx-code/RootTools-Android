package com.arthur.roottools.feature.developer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthur.roottools.automation.AutomationClientRecord
import com.arthur.roottools.automation.AutomationClientStore
import com.arthur.roottools.data.TermuxRuntimeRepository
import com.arthur.roottools.integration.termux.TermuxCliVerification
import com.arthur.roottools.integration.termux.TermuxCliProvisioner
import com.arthur.roottools.integration.termux.TermuxManagedTaskId
import com.arthur.roottools.integration.termux.TermuxMcpRelayProvisioner
import com.arthur.roottools.integration.termux.TermuxMcpRelayStatus
import com.arthur.roottools.integration.termux.TermuxMcpRelayStatusParser
import com.arthur.roottools.integration.termux.TermuxMcpRelayVerification
import com.arthur.roottools.integration.termux.TermuxRuntimeProbeParser
import com.arthur.roottools.integration.termux.TermuxSshdConfigParser
import com.arthur.roottools.integration.termux.TermuxSshdConfigSnapshot
import com.arthur.roottools.integration.termux.TermuxTaskAuditRecord
import com.arthur.roottools.integration.termux.TermuxTaskController
import com.arthur.roottools.integration.termux.TermuxTaskMutation
import com.arthur.roottools.integration.termux.TermuxManagedTaskRegistry
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
    val cliArtifactSha256: String? = null,
    val cliArtifactVersion: Int? = null,
    val cliVerification: TermuxCliVerification? = null,
    val mcpRelayArtifactPath: String? = null,
    val mcpRelayArtifactSha256: String? = null,
    val mcpRelayVersion: Int? = null,
    val mcpRelayDeviceId: String? = null,
    val mcpRelayBearerToken: String? = null,
    val mcpRelayVerification: TermuxMcpRelayVerification? = null,
    val mcpRelayStatus: TermuxMcpRelayStatus = TermuxMcpRelayStatus(),
    val sshdConfig: TermuxSshdConfigSnapshot? = null,
    val audit: List<TermuxTaskAuditRecord> = emptyList(),
    val message: String? = null,
    val error: String? = null,
)

class DeveloperRuntimeViewModel(application: Application) : AndroidViewModel(application) {
    private val runtimeRepository = TermuxRuntimeRepository(application)
    private val clientStore = AutomationClientStore(application)
    private val cliProvisioner = TermuxCliProvisioner(application)
    private val mcpProvisioner = TermuxMcpRelayProvisioner(application)
    private val taskController = TermuxTaskController(application)

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
                        val artifact = cliProvisioner.existingArtifactInfo()
                        val mcpArtifact = mcpProvisioner.existingArtifactInfo()
                        current.copy(
                            runtime = snapshot,
                            loading = false,
                            termuxClient = currentTermuxClient(),
                            cliArtifactPath = artifact?.file?.absolutePath,
                            cliArtifactSha256 = artifact?.sha256,
                            cliArtifactVersion = artifact?.version,
                            mcpRelayArtifactPath = mcpArtifact?.file?.absolutePath,
                            mcpRelayArtifactSha256 = mcpArtifact?.sha256,
                            mcpRelayVersion = mcpArtifact?.version,
                            mcpRelayDeviceId = mcpArtifact?.deviceId,
                            mcpRelayBearerToken = mcpArtifact?.bearerToken,
                            audit = taskController.readAudit(),
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
                            cliArtifactSha256 = artifact.sha256,
                            cliArtifactVersion = artifact.version,
                            cliVerification = null,
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
                    cliArtifactSha256 = null,
                    cliArtifactVersion = null,
                    cliVerification = null,
                    message = "Termux CLI credential revoked",
                    error = null,
                )
            }
        }
    }

    fun provisionMcpRelay() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, message = null) }
            val result = withContext(Dispatchers.IO) { runCatching { mcpProvisioner.provision() } }
            _state.update { current ->
                result.fold(
                    onSuccess = { artifact ->
                        current.copy(
                            loading = false,
                            mcpRelayArtifactPath = artifact.file.absolutePath,
                            mcpRelayArtifactSha256 = artifact.sha256,
                            mcpRelayVersion = artifact.version,
                            mcpRelayDeviceId = artifact.deviceId,
                            mcpRelayBearerToken = artifact.bearerToken,
                            mcpRelayVerification = null,
                            mcpRelayStatus = TermuxMcpRelayStatus(),
                            message = "MCP relay credentials rotated and artifact generated",
                        )
                    },
                    onFailure = { error ->
                        current.copy(loading = false, error = error.message ?: "MCP relay provisioning failed")
                    },
                )
            }
        }
    }

    fun revokeMcpRelay() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { mcpProvisioner.revoke() }
            _state.update {
                it.copy(
                    mcpRelayArtifactPath = null,
                    mcpRelayArtifactSha256 = null,
                    mcpRelayVersion = null,
                    mcpRelayBearerToken = null,
                    mcpRelayVerification = null,
                    mcpRelayStatus = TermuxMcpRelayStatus(),
                    message = "MCP relay credential revoked",
                    error = null,
                )
            }
        }
    }

    fun runReadOnlyTask(taskId: TermuxManagedTaskId) {
        if (TermuxManagedTaskRegistry.spec(taskId).mutation != TermuxTaskMutation.READ_ONLY) {
            _state.update { it.copy(error = "Mutation task requires an explicit controller action") }
            return
        }
        runTask(taskId) { taskController.run(taskId) }
    }

    fun installCliInTermux() {
        runTask(TermuxManagedTaskId.INSTALL_ROOTTOOLS_CLI) { taskController.installGeneratedCli() }
    }

    fun verifyCliInTermux() {
        if (!bridgeReady()) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    runningTask = TermuxManagedTaskId.VERIFY_ROOTTOOLS_CLI,
                    lastTaskResult = null,
                    error = null,
                    message = null,
                )
            }
            val verification = withContext(Dispatchers.IO) { taskController.verifyGeneratedCli() }
            _state.update { current ->
                current.copy(
                    runningTask = null,
                    lastTaskResult = verification.result,
                    cliVerification = verification,
                    audit = taskController.readAudit(),
                    error = resultError(verification.result),
                )
            }
        }
    }

    fun installDeveloperPreset() {
        runTask(TermuxManagedTaskId.INSTALL_DEVELOPER_PRESET) {
            taskController.run(TermuxManagedTaskId.INSTALL_DEVELOPER_PRESET)
        }
    }

    fun installMcpRelayInTermux() {
        runTask(TermuxManagedTaskId.INSTALL_MCP_RELAY) { taskController.installGeneratedMcpRelay() }
    }

    fun verifyMcpRelayInTermux() {
        if (!bridgeReady()) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    runningTask = TermuxManagedTaskId.VERIFY_MCP_RELAY,
                    lastTaskResult = null,
                    error = null,
                    message = null,
                )
            }
            val verification = withContext(Dispatchers.IO) { taskController.verifyGeneratedMcpRelay() }
            _state.update { current ->
                current.copy(
                    runningTask = null,
                    lastTaskResult = verification.result,
                    mcpRelayVerification = verification,
                    audit = taskController.readAudit(),
                    error = resultError(verification.result),
                )
            }
        }
    }

    fun refreshMcpRelayStatus() = runTask(TermuxManagedTaskId.MCP_RELAY_STATUS) {
        taskController.run(TermuxManagedTaskId.MCP_RELAY_STATUS)
    }

    fun startMcpRelayLoopback() = runTask(TermuxManagedTaskId.MCP_RELAY_START_LOOPBACK) {
        taskController.run(TermuxManagedTaskId.MCP_RELAY_START_LOOPBACK)
    }

    fun startMcpRelayTailscale() = runTask(TermuxManagedTaskId.MCP_RELAY_START_TAILSCALE) {
        taskController.run(TermuxManagedTaskId.MCP_RELAY_START_TAILSCALE)
    }

    fun stopMcpRelay() = runTask(TermuxManagedTaskId.MCP_RELAY_STOP) {
        taskController.run(TermuxManagedTaskId.MCP_RELAY_STOP)
    }

    fun startSshd() = runTask(TermuxManagedTaskId.SSHD_START) {
        taskController.run(TermuxManagedTaskId.SSHD_START)
    }

    fun stopSshd() = runTask(TermuxManagedTaskId.SSHD_STOP) {
        taskController.run(TermuxManagedTaskId.SSHD_STOP)
    }

    fun enableSshdAutostart() = runTask(TermuxManagedTaskId.SSHD_ENABLE_AUTOSTART) {
        taskController.run(TermuxManagedTaskId.SSHD_ENABLE_AUTOSTART)
    }

    fun disableSshdAutostart() = runTask(TermuxManagedTaskId.SSHD_DISABLE_AUTOSTART) {
        taskController.run(TermuxManagedTaskId.SSHD_DISABLE_AUTOSTART)
    }

    private fun runTask(
        taskId: TermuxManagedTaskId,
        action: suspend () -> TermuxTaskResult,
    ) {
        if (!bridgeReady()) return
        if (_state.value.runningTask != null) {
            _state.update { it.copy(error = "Another Termux task is already running") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(runningTask = taskId, lastTaskResult = null, error = null, message = null) }
            val result = withContext(Dispatchers.IO) { action() }
            applyTaskResult(taskId, result)
        }
    }

    private fun bridgeReady(): Boolean {
        if (_state.value.runtime.bridgeMode != TermuxBridgeMode.OFFICIAL_RUN_COMMAND) {
            _state.update { it.copy(error = "Official Termux RUN_COMMAND bridge is not ready") }
            return false
        }
        return true
    }

    private fun applyTaskResult(taskId: TermuxManagedTaskId, result: TermuxTaskResult) {
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
                val sshConfig = if (taskId == TermuxManagedTaskId.SSHD_CONFIG && result.success) {
                    TermuxSshdConfigParser.parse(result.stdout)
                } else current.sshdConfig
                val relayStatus = if (
                    taskId in setOf(
                        TermuxManagedTaskId.MCP_RELAY_STATUS,
                        TermuxManagedTaskId.MCP_RELAY_START_LOOPBACK,
                        TermuxManagedTaskId.MCP_RELAY_START_TAILSCALE,
                        TermuxManagedTaskId.MCP_RELAY_STOP,
                    ) && result.success
                ) {
                    val parsed = TermuxMcpRelayStatusParser.parse(result.stdout)
                    if (taskId == TermuxManagedTaskId.MCP_RELAY_STOP && parsed.running == null) {
                        TermuxMcpRelayStatus(running = false)
                    } else parsed
                } else current.mcpRelayStatus
                current.copy(
                    runtime = updatedRuntime,
                    runningTask = null,
                    lastTaskResult = result,
                    sshdConfig = sshConfig,
                    mcpRelayStatus = relayStatus,
                    audit = taskController.readAudit(),
                    error = resultError(result),
                )
            }
    }

    private fun resultError(result: TermuxTaskResult): String? = if (result.success) null else result.transportError
        ?: result.internalErrorMessage.takeIf(String::isNotBlank)
        ?: result.stderr.takeIf(String::isNotBlank)
        ?: "Termux task failed"

    fun clearMessage() {
        _state.update { it.copy(message = null, error = null) }
    }

    private fun currentTermuxClient(): AutomationClientRecord? = clientStore.listClients()
        .firstOrNull { it.clientId == AutomationClientStore.TERMUX_CLIENT_ID && !it.revoked }
}

