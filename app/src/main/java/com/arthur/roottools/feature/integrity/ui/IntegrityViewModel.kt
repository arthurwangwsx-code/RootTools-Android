package com.arthur.roottools.feature.integrity.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthur.roottools.app.RootToolsApp
import com.arthur.roottools.feature.integrity.data.IntegrityBaselineStore
import com.arthur.roottools.feature.integrity.data.IntegrityReportStore
import com.arthur.roottools.feature.integrity.data.IntegrityRepository
import com.arthur.roottools.feature.integrity.model.IntegrityBaseline
import com.arthur.roottools.feature.integrity.model.IntegrityReportFormat
import com.arthur.roottools.feature.integrity.model.IntegrityScanMode
import com.arthur.roottools.feature.integrity.model.IntegritySnapshot
import com.arthur.roottools.feature.integrity.policy.IntegrityBaselineMatcher
import com.arthur.roottools.feature.integrity.policy.IntegrityRiskEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IntegrityUiState(
    val snapshot: IntegritySnapshot = IntegritySnapshot(),
    val baseline: IntegrityBaseline? = null,
    val scanningMode: IntegrityScanMode? = null,
    val reportPath: String? = null,
    val pemPath: String? = null,
    val action: IntegrityUiAction? = null,
    val errorDetail: String? = null,
) {
    val loading: Boolean get() = scanningMode != null
}

enum class IntegrityUiAction {
    BASELINE_SAVED,
    BASELINE_CLEARED,
    REPORT_EXPORTED,
    PEM_EXPORTED,
}

class IntegrityViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = IntegrityRepository(application, (application as RootToolsApp).container.shell)
    private val baselineStore = IntegrityBaselineStore(application)
    private val reportStore = IntegrityReportStore(application)
    private val _state = MutableStateFlow(IntegrityUiState(baseline = baselineStore.read()))
    val state: StateFlow<IntegrityUiState> = _state.asStateFlow()

    fun ensureFastScan() {
        if (_state.value.snapshot.scannedAtEpochMs > 0L || _state.value.loading) return
        scan(IntegrityScanMode.FAST)
    }

    fun scan(mode: IntegrityScanMode) {
        if (_state.value.loading) return
        viewModelScope.launch {
            val before = _state.value
            _state.update { it.copy(scanningMode = mode, action = null, errorDetail = null) }
            runCatching {
                repository.scan(
                    mode = mode,
                    baseline = before.baseline,
                    previous = before.snapshot.takeIf { it.scannedAtEpochMs > 0L },
                )
            }.onSuccess { snapshot ->
                _state.update { it.copy(snapshot = snapshot, scanningMode = null, errorDetail = null) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        scanningMode = null,
                        errorDetail = error.message ?: error.javaClass.simpleName,
                    )
                }
            }
        }
    }

    fun saveCurrentBaseline(profileName: String) {
        val snapshot = _state.value.snapshot
        if (snapshot.scannedAtEpochMs == 0L) return
        val baseline = IntegrityBaselineMatcher.capture(
            profileName = profileName,
            signals = snapshot.signals,
            nowEpochMs = System.currentTimeMillis(),
        )
        baselineStore.save(baseline)
        _state.update {
            it.copy(
                baseline = baseline,
                snapshot = snapshot.copy(
                    findings = IntegrityRiskEngine.evaluate(snapshot.signals, baseline),
                    baselineProfileName = baseline.profileName,
                ),
                action = IntegrityUiAction.BASELINE_SAVED,
                errorDetail = null,
            )
        }
    }

    fun clearBaseline() {
        baselineStore.clear()
        val snapshot = _state.value.snapshot
        _state.update {
            it.copy(
                baseline = null,
                snapshot = snapshot.copy(
                    findings = IntegrityRiskEngine.evaluate(snapshot.signals, null),
                    baselineProfileName = null,
                ),
                action = IntegrityUiAction.BASELINE_CLEARED,
                errorDetail = null,
            )
        }
    }

    fun exportReport(format: IntegrityReportFormat) {
        if (format == IntegrityReportFormat.PEM) {
            exportPem()
            return
        }
        val snapshot = _state.value.snapshot
        if (snapshot.scannedAtEpochMs == 0L) return
        viewModelScope.launch {
            runCatching { reportStore.write(snapshot, format) }
                .onSuccess { file ->
                    _state.update {
                        it.copy(reportPath = file.absolutePath, action = IntegrityUiAction.REPORT_EXPORTED, errorDetail = null)
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(errorDetail = error.message ?: error.javaClass.simpleName) }
                }
        }
    }

    fun exportPem() {
        viewModelScope.launch {
            runCatching { repository.exportAttestationPem() }
                .onSuccess { file ->
                    _state.update {
                        if (file != null) {
                            it.copy(pemPath = file.absolutePath, action = IntegrityUiAction.PEM_EXPORTED, errorDetail = null)
                        } else {
                            it.copy(errorDetail = "attestation certificate chain is not available")
                        }
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(errorDetail = error.message ?: error.javaClass.simpleName) }
                }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(action = null, errorDetail = null) }
    }
}
