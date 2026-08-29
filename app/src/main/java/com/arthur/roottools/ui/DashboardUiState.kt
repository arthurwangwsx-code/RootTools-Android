package com.arthur.roottools.ui

import com.arthur.roottools.model.AdbSnapshot
import com.arthur.roottools.model.AppControlDetail
import com.arthur.roottools.model.AppControlExportResult
import com.arthur.roottools.model.AppInventorySnapshot
import com.arthur.roottools.model.AppRuntimeSnapshot
import com.arthur.roottools.model.AppActionPlan
import com.arthur.roottools.model.AppPolicyProfileId
import com.arthur.roottools.model.CapabilityProbeResult
import com.arthur.roottools.model.ComponentSnapshot
import com.arthur.roottools.model.CpuCapState
import com.arthur.roottools.model.CpuPolicyEvent
import com.arthur.roottools.model.DeviceHealthSnapshot
import com.arthur.roottools.model.DeviceSnapshot
import com.arthur.roottools.model.DiagnosticsSnapshot
import com.arthur.roottools.model.HealthHistoryPoint
import com.arthur.roottools.model.ModuleCenterSnapshot
import com.arthur.roottools.model.NetworkSnapshot
import com.arthur.roottools.model.PermissionAppOpsSnapshot
import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.model.PingResult
import com.arthur.roottools.model.FrameworkPrivilegePreference
import com.arthur.roottools.model.RootActionAuditRecord
import com.arthur.roottools.model.RootShellDetails
import com.arthur.roottools.model.ShizukuBridgeState
import com.arthur.roottools.model.StartupAnalysis
import com.arthur.roottools.model.StorageSnapshot
import com.arthur.roottools.model.SystemActionId
import com.arthur.roottools.root.RootAuthorizationSnapshot

/**
 * Temporary aggregate state for the legacy dashboard host.
 *
 * Feature extraction should move complete feature state out of this class. Keeping the aggregate in
 * its own file prevents the ViewModel from also being the canonical state schema.
 */
data class DashboardUiState(
    val loading: Boolean = true,
    val actionInProgress: Boolean = false,
    val snapshot: DeviceSnapshot = DeviceSnapshot(),
    val health: DeviceHealthSnapshot = DeviceHealthSnapshot(),
    val healthHistory: List<HealthHistoryPoint> = emptyList(),
    val dailyHealthHistory: List<HealthHistoryPoint> = emptyList(),
    val detailSamplingSeconds: Int = 2,
    val cpuCapStates: List<CpuCapState> = emptyList(),
    val cpuPolicyEvents: List<CpuPolicyEvent> = emptyList(),
    val mode: PerformanceMode = PerformanceMode.AUTO,
    val notificationsGranted: Boolean = false,
    val rootAuthorization: RootAuthorizationSnapshot = RootAuthorizationSnapshot(),
    val frameworkPrivilegePreference: FrameworkPrivilegePreference = FrameworkPrivilegePreference.AUTO,
    val startup: StartupAnalysis = StartupAnalysis(),
    val startupLoading: Boolean = false,
    val diagnostics: DiagnosticsSnapshot = DiagnosticsSnapshot(),
    val diagnosticsLoading: Boolean = false,
    val rootShellDetails: Map<Int, RootShellDetails> = emptyMap(),
    val diagnosticText: String = "",
    val modules: ModuleCenterSnapshot = ModuleCenterSnapshot(),
    val modulesLoading: Boolean = false,
    val pendingRebootModules: Set<String> = emptySet(),
    val network: NetworkSnapshot = NetworkSnapshot(),
    val networkLoading: Boolean = false,
    val pingResult: PingResult? = null,
    val storage: StorageSnapshot = StorageSnapshot(),
    val storageLoading: Boolean = false,
    val automationToken: String = "",
    val lastReportPath: String? = null,
    val favoriteActions: Set<SystemActionId> = emptySet(),
    val auditRecords: List<RootActionAuditRecord> = emptyList(),
    val adb: AdbSnapshot = AdbSnapshot(),
    val adbLoading: Boolean = false,
    val shizuku: ShizukuBridgeState = ShizukuBridgeState(),
    val shizukuSelfTest: List<CapabilityProbeResult> = emptyList(),
    val shizukuSelfTestLoading: Boolean = false,
    val componentSnapshot: ComponentSnapshot? = null,
    val componentCatalog: List<Pair<String, String>> = emptyList(),
    val componentLoading: Boolean = false,
    val permissionOpsSnapshot: PermissionAppOpsSnapshot? = null,
    val permissionOpsLoading: Boolean = false,
    val appInventory: AppInventorySnapshot = AppInventorySnapshot(),
    val appInventoryLoading: Boolean = false,
    val appControlDetail: AppControlDetail? = null,
    val appControlDetailLoading: Boolean = false,
    val appControlExport: AppControlExportResult? = null,
    val appRuntime: AppRuntimeSnapshot? = null,
    val appRuntimeLoading: Boolean = false,
    val appBatchSelection: Set<String> = emptySet(),
    val appBatchProfile: AppPolicyProfileId = AppPolicyProfileId.RESTRICTED,
    val appBatchPlan: AppActionPlan? = null,
    val appBatchLastApplied: AppActionPlan? = null,
    val appBatchLoading: Boolean = false,
    val actionMessage: String? = null,
    val error: String? = null,
)
