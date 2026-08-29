package com.aibox.backgroundserver

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aibox.backgroundserver.domain.NetworkCapabilities
import com.aibox.backgroundserver.domain.NetworkSnapshot
import com.aibox.backgroundserver.domain.PowerSettings
import com.aibox.backgroundserver.domain.RootStatus
import com.aibox.backgroundserver.domain.TunnelRuntimeState
import com.aibox.backgroundserver.engine.wireguard.WireGuardServerManager
import com.aibox.backgroundserver.engine.wireguard.WireGuardRuntime
import com.aibox.backgroundserver.platform.network.NetworkCapabilityProbe
import com.aibox.backgroundserver.platform.network.NetworkInspector
import com.aibox.backgroundserver.platform.power.PowerController
import com.aibox.backgroundserver.platform.root.RootCommandGateway
import com.aibox.backgroundserver.runtime.BackgroundRuntimeService
import com.aibox.backgroundserver.runtime.RuntimeMetricsStore
import com.aibox.backgroundserver.runtime.RuntimePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val root = RootCommandGateway()
    private val powerController = PowerController(application, root)
    private val networkCapabilityProbe = NetworkCapabilityProbe(root)
    private val wireGuardServer = WireGuardRuntime.get(application)
    private val runtimePreferences = RuntimePreferences(application)

    private val _rootStatus = MutableStateFlow(RootStatus())
    val rootStatus: StateFlow<RootStatus> = _rootStatus.asStateFlow()

    private val _powerSettings = MutableStateFlow(PowerSettings())
    val powerSettings: StateFlow<PowerSettings> = _powerSettings.asStateFlow()

    private val _network = MutableStateFlow(NetworkSnapshot())
    val network: StateFlow<NetworkSnapshot> = _network.asStateFlow()

    private val _networkCapabilities = MutableStateFlow(NetworkCapabilities())
    val networkCapabilities: StateFlow<NetworkCapabilities> = _networkCapabilities.asStateFlow()

    private val _softBlanked = MutableStateFlow(false)
    val softBlanked: StateFlow<Boolean> = _softBlanked.asStateFlow()

    val wireGuardState = wireGuardServer.state

    val runtimeMetrics = RuntimeMetricsStore.metrics

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { root.probe() }
            _rootStatus.value = RootStatus(
                available = result.ok && result.stdout.contains("uid=0"),
                detail = when {
                    result.ok && result.stdout.contains("uid=0") -> "Root 已授权"
                    result.ok -> result.stdout.ifBlank { "su 可用，但未获得 uid=0" }
                    else -> result.stderr.ifBlank { "Root 不可用" }
                },
            )
            refreshPowerSettings()
            refreshNetwork()
            refreshNetworkCapabilities()
        }
    }

    fun refreshPowerSettings() {
        viewModelScope.launch {
            val doubleTap = withContext(Dispatchers.IO) { powerController.readDoubleTapToWake() }
            _powerSettings.update {
                it.copy(
                    doubleTapToWake = doubleTap,
                    interactive = powerController.isInteractive(),
                    screenOffWorkEnabled = runtimeMetrics.value.running,
                    restoreAfterBoot = runtimePreferences.restoreAfterBoot,
                    screenOffWithoutLock = runtimePreferences.screenOffWithoutLock,
                )
            }
        }
    }

    fun refreshNetwork() {
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = NetworkInspector.snapshot(getApplication())
            _network.value = snapshot
            wireGuardServer.updateEndpointHost(snapshot.wifiLikeIpv4)
        }
    }

    fun refreshNetworkCapabilities() {
        viewModelScope.launch(Dispatchers.IO) {
            _networkCapabilities.value = networkCapabilityProbe.probe()
            wireGuardServer.refreshPermissionState()
        }
    }

    fun wireGuardVpnPermissionIntent(): Intent? = wireGuardServer.vpnPermissionIntent()

    fun startWireGuardServer() {
        BackgroundRuntimeService.startWireGuard(getApplication())
    }

    fun stopWireGuardServer() {
        BackgroundRuntimeService.stopWireGuard(getApplication())
    }

    fun setScreenOffWork(enabled: Boolean) {
        val context = getApplication<Application>()
        if (enabled) BackgroundRuntimeService.start(context) else BackgroundRuntimeService.stop(context)
        _powerSettings.update { it.copy(screenOffWorkEnabled = enabled) }
    }

    fun setRestoreAfterBoot(enabled: Boolean) {
        runtimePreferences.restoreAfterBoot = enabled
        _powerSettings.update { it.copy(restoreAfterBoot = enabled) }
    }

    fun setScreenOffWithoutLock(enabled: Boolean) {
        runtimePreferences.screenOffWithoutLock = enabled
        _powerSettings.update { it.copy(screenOffWithoutLock = enabled) }
        if (!enabled) _softBlanked.value = false
    }

    fun blankOrSleepDisplay() {
        if (runtimePreferences.screenOffWithoutLock) {
            _softBlanked.value = true
        } else {
            viewModelScope.launch(Dispatchers.IO) { powerController.sleepDisplay() }
        }
    }

    fun restoreSoftBlank() {
        _softBlanked.value = false
    }

    fun wakeDisplay() {
        viewModelScope.launch(Dispatchers.IO) { powerController.wakeDisplay() }
    }

    fun setDoubleTapToWake(enabled: Boolean) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { powerController.setDoubleTapToWake(enabled) }
            if (result.ok) _powerSettings.update { it.copy(doubleTapToWake = enabled) }
        }
    }
}
