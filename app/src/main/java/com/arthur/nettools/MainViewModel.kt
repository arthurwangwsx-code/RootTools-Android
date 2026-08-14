package com.arthur.nettools

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthur.nettools.capture.AppTarget
import com.arthur.nettools.capture.CaptureRepository
import com.arthur.nettools.capture.CaptureState
import com.arthur.nettools.capture.RootShell
import com.arthur.nettools.intercept.AddonStatus
import com.arthur.nettools.intercept.InterceptionEngine
import com.arthur.nettools.intercept.InterceptionOptions
import com.arthur.nettools.intercept.InterceptionRuntime
import com.arthur.nettools.intercept.InterceptionService
import com.arthur.nettools.intercept.InterceptionState
import com.arthur.nettools.intercept.InterceptionSession
import com.arthur.nettools.intercept.InterceptionStore
import com.arthur.nettools.intercept.DecryptedEvent
import com.arthur.nettools.intercept.MitmAddonClient
import com.arthur.nettools.intercept.MitmAddonManager
import com.arthur.nettools.security.CaStatus
import com.arthur.nettools.security.CertificateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CaptureRepository(app)
    private val caManager = CertificateManager(app)
    private val addonManager = MitmAddonManager(app)
    private val interceptionStore = InterceptionStore(app)
    val captureState: StateFlow<CaptureState> = repo.state
    val interceptionState: StateFlow<InterceptionState> = InterceptionRuntime.state
    private val appsMutable = MutableStateFlow<List<AppTarget>>(emptyList())
    val apps = appsMutable.asStateFlow()
    private val caMutable = MutableStateFlow(caManager.status())
    val ca: StateFlow<CaStatus> = caMutable.asStateFlow()
    private val addonMutable = MutableStateFlow(addonManager.installedStatus())
    val addon: StateFlow<AddonStatus> = addonMutable.asStateFlow()
    private val interceptionHistoryMutable = MutableStateFlow<List<InterceptionSession>>(emptyList())
    val interceptionHistory: StateFlow<List<InterceptionSession>> = interceptionHistoryMutable.asStateFlow()
    private val inspectedInterceptionMutable = MutableStateFlow<InterceptionSession?>(null)
    val inspectedInterception: StateFlow<InterceptionSession?> = inspectedInterceptionMutable.asStateFlow()
    private val inspectedEventsMutable = MutableStateFlow<List<DecryptedEvent>>(emptyList())
    val inspectedEvents: StateFlow<List<DecryptedEvent>> = inspectedEventsMutable.asStateFlow()
    private val actionMutable = MutableStateFlow<String?>(null)
    val actionMessage = actionMutable.asStateFlow()

    init {
        viewModelScope.launch {
            if (InterceptionRuntime.state.value.phase.name == "IDLE") InterceptionEngine.cleanupNetworkRules()
            repo.initialize()
            appsMutable.value = repo.installedApps()
            caMutable.value = caManager.status()
            addonMutable.value = addonManager.queryLatest()
            interceptionHistoryMutable.value = interceptionStore.loadSessions()
            InterceptionRuntime.update {
                it.copy(rootAvailable = RootShell.hasRoot(), addon = addonMutable.value)
            }
        }
        viewModelScope.launch {
            interceptionState.map { it.phase }.distinctUntilChanged().collect { phase ->
                if (phase.name == "IDLE" || phase.name == "ERROR") {
                    interceptionHistoryMutable.value = interceptionStore.loadSessions()
                }
            }
        }
    }

    fun start(target: AppTarget?) = viewModelScope.launch { repo.start(target) }
    fun stop() = viewModelScope.launch { repo.stop() }
    fun prepareCaptureSession(id: String) = viewModelScope.launch { repo.ensurePacketAnalysis(id) }
    fun refresh() = viewModelScope.launch {
        repo.initialize()
        appsMutable.value = repo.installedApps()
        caMutable.value = caManager.status()
        addonMutable.value = addonManager.queryLatest()
        interceptionHistoryMutable.value = interceptionStore.loadSessions()
        InterceptionRuntime.update { it.copy(rootAvailable = RootShell.hasRoot(), addon = addonMutable.value) }
    }
    fun generateCa() = viewModelScope.launch {
        caMutable.value = caManager.generate()
        actionMutable.value = "Private CA generated on this device"
    }
    fun installCa() = viewModelScope.launch {
        caManager.installSystemModule().fold(
            { caMutable.value = it; actionMutable.value = "Magisk CA module installed. Reboot is required." },
            { actionMutable.value = "CA install failed: ${it.message}" },
        )
    }
    fun removeCa() = viewModelScope.launch {
        caManager.removeSystemModule().fold(
            { caMutable.value = caManager.status(); actionMutable.value = "CA module removed. Reboot to apply." },
            { actionMutable.value = "Remove failed: ${it.message}" },
        )
    }

    fun installMitmAddon() = viewModelScope.launch {
        addonMutable.value = addonMutable.value.copy(busy = true, message = "Preparing add-on")
        addonManager.installLatest { message ->
            addonMutable.value = addonMutable.value.copy(busy = true, message = message)
        }.fold(
            { status ->
                addonMutable.value = status.copy(busy = false)
                InterceptionRuntime.update { it.copy(addon = addonMutable.value) }
                actionMutable.value = "MITM add-on ${status.versionName} installed"
            },
            { error ->
                addonMutable.value = addonManager.installedStatus().copy(busy = false, message = error.message)
                actionMutable.value = "Add-on install failed: ${error.message}"
            },
        )
    }

    fun importMitmCa() = viewModelScope.launch {
        val client = MitmAddonClient(getApplication())
        runCatching {
            check(addonManager.installedStatus().installed) { "Install the MITM add-on first" }
            val pem = client.requestCertificate()
            check(pem.contains("BEGIN CERTIFICATE")) { "MITM add-on returned an invalid certificate" }
            caManager.importPem(pem, "PCAPdroid MITM / mitmproxy")
        }.fold(
            { status -> caMutable.value = status; actionMutable.value = "MITM interception CA imported" },
            { actionMutable.value = "CA import failed: ${it.message}" },
        )
        client.disconnect()
    }

    fun startInterception(target: AppTarget, options: InterceptionOptions) {
        val intent = Intent(getApplication(), InterceptionService::class.java).apply {
            action = InterceptionService.ACTION_START
            putExtra(InterceptionService.EXTRA_LABEL, target.label)
            putExtra(InterceptionService.EXTRA_PACKAGE, target.packageName)
            putExtra(InterceptionService.EXTRA_UID, target.uid)
            putExtra(InterceptionService.EXTRA_BLOCK_QUIC, options.blockQuic)
            putExtra(InterceptionService.EXTRA_RESTART, options.restartTarget)
            putExtra(InterceptionService.EXTRA_FULL_PAYLOAD, options.fullPayload)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    fun stopInterception() {
        getApplication<Application>().startService(Intent(getApplication(), InterceptionService::class.java).apply { action = InterceptionService.ACTION_STOP })
    }

    fun inspectInterceptionSession(id: String) = viewModelScope.launch(Dispatchers.IO) {
        val session = interceptionStore.findSession(id)
        inspectedInterceptionMutable.value = session
        inspectedEventsMutable.value = session?.let { interceptionStore.loadEvents(it) } ?: emptyList()
    }

    fun interceptionStorageBytes(): Long = interceptionStore.totalSizeBytes()

    fun cleanupInterceptionRules() = viewModelScope.launch {
        val result = InterceptionEngine.cleanupNetworkRules()
        actionMutable.value = if (result.isBlank()) "Transparent proxy rules cleaned" else "Rules cleaned: $result"
    }

    fun rebootDevice() = viewModelScope.launch {
        actionMutable.value = "Rebooting device to apply system trust changes"
        RootShell.exec("reboot")
    }

    fun uninstallMitmAddon() = viewModelScope.launch {
        val result = RootShell.exec("pm uninstall com.pcapdroid.mitm")
        addonMutable.value = addonManager.installedStatus()
        InterceptionRuntime.update { it.copy(addon = addonMutable.value) }
        actionMutable.value = if (result.code == 0) "MITM add-on removed" else "Add-on removal failed: ${result.output}"
    }
    fun clearMessage() { actionMutable.value = null }
}
