package com.arthur.roottools.feature.network.inspection.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthur.roottools.app.rootToolsContainer
import com.arthur.roottools.feature.network.inspection.capture.AppTarget
import com.arthur.roottools.feature.network.inspection.intercept.AddonStatus
import com.arthur.roottools.feature.network.inspection.intercept.CertificateSource
import com.arthur.roottools.feature.network.inspection.intercept.InterceptionCertificateStatus
import com.arthur.roottools.feature.network.inspection.intercept.InterceptionOptions
import com.arthur.roottools.feature.network.inspection.intercept.InterceptionRuntime
import com.arthur.roottools.feature.network.inspection.intercept.InterceptionService
import com.arthur.roottools.feature.network.inspection.intercept.InterceptionSession
import com.arthur.roottools.feature.network.inspection.intercept.InterceptionState
import com.arthur.roottools.feature.network.inspection.intercept.MitmAddonClient
import com.arthur.roottools.feature.network.inspection.intercept.MitmAddonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NetworkInterceptionUiState(
    val runtime: InterceptionState = InterceptionState(),
    val addon: AddonStatus = AddonStatus(),
    val certificate: InterceptionCertificateStatus = InterceptionCertificateStatus(),
    val installedApps: List<AppTarget> = emptyList(),
    val selectedTarget: AppTarget? = null,
    val sessions: List<InterceptionSession> = emptyList(),
    val options: InterceptionOptions = InterceptionOptions(),
    val busy: Boolean = false,
    val actionFailed: Boolean = false,
)

class NetworkInterceptionViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.rootToolsContainer
    private val addonRepository = container.mitmAddonRepository
    private val certificateManager = container.interceptionCertificateManager
    private val networkController = container.interceptionNetworkController
    private val store = container.interceptionStore
    private val mutableState = MutableStateFlow(NetworkInterceptionUiState())
    val state: StateFlow<NetworkInterceptionUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            InterceptionRuntime.state.collect { runtime ->
                mutableState.update { it.copy(runtime = runtime) }
            }
        }
        refresh()
    }

    fun refresh() = runAction {
        val addon = addonRepository.installedStatus()
        val certificate = certificateManager.status()
        val apps = container.networkCaptureRepository.installedApps()
        val sessions = store.loadSessions()
        mutableState.update { current ->
            current.copy(
                addon = addon,
                certificate = certificate,
                installedApps = apps,
                selectedTarget = current.selectedTarget?.let { selected ->
                    apps.firstOrNull { it.packageName == selected.packageName && it.uid == selected.uid }
                },
                sessions = sessions,
            )
        }
    }

    fun selectTarget(target: AppTarget) {
        if (mutableState.value.busy || mutableState.value.runtime.target != null) return
        mutableState.update { it.copy(selectedTarget = target) }
    }

    fun updateOptions(options: InterceptionOptions) {
        if (mutableState.value.busy || mutableState.value.runtime.target != null) return
        mutableState.update { it.copy(options = options) }
    }

    fun importAddonCertificate() = runAction {
        val client = MitmAddonClient(getApplication())
        try {
            check(addonRepository.installedStatus().installed) { "MITM add-on is not installed" }
            val pem = client.requestCertificate()
            certificateManager.importPem(pem, CertificateSource.MITM_ADDON)
        } finally {
            client.disconnect()
        }
        refreshStateOnly()
    }

    fun generateStandaloneCertificate() = runAction {
        certificateManager.generateStandalone()
        refreshStateOnly()
    }

    fun installCertificateModule() = runAction {
        certificateManager.installSystemModule().getOrThrow()
        refreshStateOnly()
    }

    fun removeCertificateModule() = runAction {
        certificateManager.removeSystemModule().getOrThrow()
        refreshStateOnly()
    }

    fun cleanupRules() = runAction {
        check(networkController.cleanupRules().success) { "Interception rule cleanup failed" }
    }

    fun startInterception() {
        val current = mutableState.value
        val target = current.selectedTarget ?: return
        if (current.busy || current.runtime.target != null) return
        getApplication<Application>().startForegroundService(
            InterceptionService.startIntent(getApplication(), target, current.options),
        )
    }

    fun stopInterception() {
        getApplication<Application>().startService(InterceptionService.stopIntent(getApplication()))
    }

    fun openAddonPage() {
        getApplication<Application>().startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(ADDON_RELEASES_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openAddonSettings() {
        getApplication<Application>().startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${MitmAddonRepository.PACKAGE_NAME}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun clearActionFailure() {
        mutableState.update { it.copy(actionFailed = false) }
    }

    private suspend fun refreshStateOnly() {
        val addon = addonRepository.installedStatus()
        val certificate = certificateManager.status()
        val sessions = store.loadSessions()
        mutableState.update { it.copy(addon = addon, certificate = certificate, sessions = sessions) }
    }

    private fun runAction(action: suspend () -> Unit) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, actionFailed = false) }
            runCatching { action() }
                .onSuccess { mutableState.update { it.copy(busy = false) } }
                .onFailure { mutableState.update { it.copy(busy = false, actionFailed = true) } }
        }
    }

    private companion object {
        const val ADDON_RELEASES_URL = "https://github.com/emanuele-f/PCAPdroid-mitm/releases"
    }
}
