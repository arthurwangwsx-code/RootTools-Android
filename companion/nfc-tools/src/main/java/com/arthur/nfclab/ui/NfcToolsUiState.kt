package com.arthur.nfclab.ui

import com.arthur.nfclab.domain.AccessDiagnosticReport
import com.arthur.nfclab.domain.AccessReaderOutcome
import com.arthur.nfclab.domain.NfcDeviceProfile
import com.arthur.nfclab.domain.NfcOperatingMode
import com.arthur.nfclab.domain.ProvisioningCapabilityReport
import com.arthur.nfclab.hce.HceCompatibilityTrace
import com.arthur.nfclab.nfc.TagSnapshot

enum class AccessDiagnosticPhase {
    IDLE,
    STARTING,
    RUNNING,
    ANALYZING,
    COMPLETE,
    ERROR,
}

data class AccessDiagnosticUiState(
    val supported: Boolean = false,
    val providerId: String? = null,
    val phase: AccessDiagnosticPhase = AccessDiagnosticPhase.IDLE,
    val currentCardTitle: String? = null,
    val report: AccessDiagnosticReport? = null,
    val history: List<AccessDiagnosticReport> = emptyList(),
    val error: String? = null,
)

data class NfcToolsUiState(
    val operatingMode: NfcOperatingMode,
    val nfcAvailable: Boolean,
    val nfcEnabled: Boolean,
    val supportsHce: Boolean,
    val supportsHceF: Boolean,
    val lastSnapshot: TagSnapshot?,
    val history: List<TagSnapshot>,
    val hcePayload: String,
    val hceTrace: HceCompatibilityTrace,
    val rootReport: String,
    val rootRunning: Boolean,
    val deviceProfile: NfcDeviceProfile?,
    val deviceProfileLoading: Boolean,
    val provisioningCapability: ProvisioningCapabilityReport?,
    val accessDiagnostic: AccessDiagnosticUiState,
)

data class NfcToolsActions(
    val onModeChange: (NfcOperatingMode) -> Unit,
    val onClearHistory: () -> Unit,
    val onHcePayloadChange: (String) -> Unit,
    val onRunRootDiagnostics: () -> Unit,
    val onRefreshDeviceProfile: () -> Unit,
    val onOpenWallet: (providerId: String) -> Unit,
    val onStartAccessDiagnostic: () -> Unit,
    val onFinishAccessDiagnostic: (AccessReaderOutcome) -> Unit,
    val onClearAccessDiagnosticHistory: () -> Unit,
)
