package com.arthur.roottools.app

import android.app.Application
import com.arthur.roottools.automation.ActionTokenStore
import com.arthur.roottools.data.AdbRepository
import com.arthur.roottools.data.AppControlDiagnosticStore
import com.arthur.roottools.data.AppControlRepository
import com.arthur.roottools.data.AppRuntimeRepository
import com.arthur.roottools.data.AppActionPlanStore
import com.arthur.roottools.data.ComponentRepository
import com.arthur.roottools.data.DeviceRepository
import com.arthur.roottools.data.DeviceSamplerService
import com.arthur.roottools.data.DiagnosticReportStore
import com.arthur.roottools.data.DiagnosticsRepository
import com.arthur.roottools.data.LagForensicsMonitor
import com.arthur.roottools.data.LagForensicsStore
import com.arthur.roottools.data.ModuleCenterRepository
import com.arthur.roottools.data.NetworkRepository
import com.arthur.roottools.data.PermissionAppOpsRepository
import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.data.StartupRepository
import com.arthur.roottools.data.StorageRepository
import com.arthur.roottools.feature.adgovernance.data.AdGovernanceRepository
import com.arthur.roottools.feature.agent.AgentSessionManager
import com.arthur.roottools.feature.agent.data.AgentSessionStore
import com.arthur.roottools.feature.network.tailscale.RootTailscaleAuditSink
import com.arthur.roottools.feature.network.tailscale.RootTailscaleController
import com.arthur.roottools.feature.network.tailscale.data.RootTailscaleRepository
import com.arthur.roottools.feature.network.inspection.data.NetworkCaptureRepository
import com.arthur.roottools.feature.network.inspection.intercept.InterceptionNetworkController
import com.arthur.roottools.feature.network.inspection.intercept.InterceptionAuditSink
import com.arthur.roottools.feature.network.inspection.intercept.InterceptionStore
import com.arthur.roottools.feature.network.inspection.intercept.MitmAddonRepository
import com.arthur.roottools.feature.network.tailscale.data.RootTailscaleRuntimeInstaller
import com.arthur.roottools.feature.assistant.data.AssistantRepository
import com.arthur.roottools.app.assistant.AssistantController
import com.arthur.roottools.policy.ActionFavoritesStore
import com.arthur.roottools.policy.AdbController
import com.arthur.roottools.policy.AppOpsPolicyController
import com.arthur.roottools.policy.AppBatchPolicyEngine
import com.arthur.roottools.policy.BatteryPolicyController
import com.arthur.roottools.policy.ComponentPolicyController
import com.arthur.roottools.policy.CpuPolicyController
import com.arthur.roottools.policy.CpuPolicyEventStore
import com.arthur.roottools.policy.CpuPolicyInspector
import com.arthur.roottools.policy.PackagePolicyController
import com.arthur.roottools.policy.PermissionPolicyController
import com.arthur.roottools.policy.PolicyStore
import com.arthur.roottools.policy.SystemActionController
import com.arthur.roottools.policy.ShadowDisplayController
import com.arthur.roottools.privilege.PrivilegeRouter
import com.arthur.roottools.privilege.PrivilegePreferenceStore
import com.arthur.roottools.privilege.ShizukuBridge
import com.arthur.roottools.privilege.ShizukuUserServiceClient
import com.arthur.roottools.root.RootAuthorizationManager
import com.arthur.roottools.root.RootShell

/**
 * Process composition root.
 *
 * Keep lifecycle-bearing infrastructure here so screens/ViewModels consume dependencies instead of
 * constructing their own privileged execution graph. Feature-specific factories can be split out
 * later without changing the process singleton boundaries for RootShell / Shizuku / audit.
 */
internal class AppContainer(private val application: Application) {
    val shell by lazy { RootShell() }
    val rootAuthorizationManager by lazy { RootAuthorizationManager(shell) }
    val shizukuBridge by lazy { ShizukuBridge(application) }
    val shizukuUserService by lazy { ShizukuUserServiceClient(application) }
    val privilegePreferenceStore by lazy { PrivilegePreferenceStore(application) }
    val privilegeRouter by lazy {
        PrivilegeRouter(
            bridge = shizukuBridge,
            shizukuClient = shizukuUserService,
            rootShell = shell,
            rootAvailable = { rootAuthorizationManager.state.value.granted },
            frameworkPreference = { privilegePreferenceStore.frameworkPreference },
        )
    }

    val auditStore by lazy { RootActionAuditStore(application) }
    val policyStore by lazy { PolicyStore(application) }
    val policyEventStore by lazy { CpuPolicyEventStore(application) }
    val policyInspector by lazy { CpuPolicyInspector(policyStore) }

    val cpuPolicyController by lazy {
        createCpuPolicyController(UI_AUDIT_SOURCE)
    }

    fun createCpuPolicyController(auditSource: String) =
        CpuPolicyController(
            shell = shell,
            store = policyStore,
            eventStore = policyEventStore,
            auditStore = auditStore,
            auditSource = auditSource,
        )

    val deviceRepository by lazy {
        DeviceRepository(shell) { rootAuthorizationManager.state.value.granted }
    }
    val adbRepository by lazy {
        AdbRepository(application, shell) { rootAuthorizationManager.state.value.granted }
    }
    val adbController by lazy { createAdbController(UI_AUDIT_SOURCE) }

    fun createAdbController(auditSource: String) =
        AdbController(application, shell, auditStore, auditSource)
    val sampler by lazy { DeviceSamplerService(application) }
    val startupRepository by lazy { StartupRepository(application, shell) }
    val diagnosticsRepository by lazy { DiagnosticsRepository(shell) }
    val lagForensicsStore by lazy { LagForensicsStore(application) }
    val lagForensicsMonitor by lazy { LagForensicsMonitor(shell, lagForensicsStore) }
    val moduleCenterRepository by lazy { ModuleCenterRepository(shell, auditStore, UI_AUDIT_SOURCE) }
    val networkRepository by lazy { NetworkRepository(shell) }
    val networkCaptureRepository by lazy {
        NetworkCaptureRepository(application, shell) { rootAuthorizationManager.state.value.granted }
    }
    val interceptionNetworkController by lazy {
        InterceptionNetworkController(
            shell = shell,
            auditSink = InterceptionAuditSink { record ->
                auditStore.record(
                    source = UI_AUDIT_SOURCE,
                    feature = "network_interception",
                    action = record.action,
                    target = record.target,
                    before = record.before,
                    after = record.after,
                    success = record.success,
                    rollbackHint = record.rollbackHint,
                )
            },
        )
    }
    val mitmAddonRepository by lazy { MitmAddonRepository(application) }
    val interceptionStore by lazy { InterceptionStore(application) }
    val rootTailscaleRepository by lazy {
        RootTailscaleRepository(shell) { rootAuthorizationManager.state.value.granted }
    }
    val rootTailscaleRuntimeInstaller by lazy { RootTailscaleRuntimeInstaller(application) }
    val rootTailscaleController by lazy {
        RootTailscaleController(
            shell = shell,
            privilegeRouter = privilegeRouter,
            repository = rootTailscaleRepository,
            installer = rootTailscaleRuntimeInstaller,
            auditSink = RootTailscaleAuditSink { record ->
                auditStore.record(
                    source = UI_AUDIT_SOURCE,
                    feature = "root_tailscale",
                    action = record.action,
                    target = record.target,
                    before = record.before,
                    after = record.after,
                    success = record.success,
                    rollbackHint = record.rollbackHint,
                )
            },
        )
    }
    val adGovernanceRepository by lazy { AdGovernanceRepository(shell) }
    val storageRepository by lazy { StorageRepository(shell) }
    val batteryPolicyController by lazy { BatteryPolicyController(shell, auditStore, UI_AUDIT_SOURCE) }
    val systemActionController by lazy {
        SystemActionController(
            shell = shell,
            auditStore = auditStore,
            auditSource = UI_AUDIT_SOURCE,
            batteryController = batteryPolicyController,
        )
    }

    val appControlRepository by lazy { AppControlRepository(application, shell) }
    val appControlDiagnosticStore by lazy { AppControlDiagnosticStore(application) }
    val appRuntimeRepository by lazy { AppRuntimeRepository(privilegeRouter) }
    val appActionPlanStore by lazy { AppActionPlanStore(application) }
    val appBatchPolicyEngine by lazy { AppBatchPolicyEngine(privilegeRouter, appActionPlanStore, auditStore) }
    val componentRepository by lazy { ComponentRepository(application) }
    val permissionAppOpsRepository by lazy { PermissionAppOpsRepository(application, privilegeRouter) }
    val packagePolicyController by lazy { createPackagePolicyController(UI_AUDIT_SOURCE) }

    fun createPackagePolicyController(auditSource: String) =
        PackagePolicyController(privilegeRouter, auditStore, auditSource)
    val componentPolicyController by lazy { ComponentPolicyController(privilegeRouter, auditStore, UI_AUDIT_SOURCE) }
    val appOpsPolicyController by lazy { AppOpsPolicyController(privilegeRouter, auditStore, UI_AUDIT_SOURCE) }
    val permissionPolicyController by lazy { PermissionPolicyController(privilegeRouter, auditStore, UI_AUDIT_SOURCE) }
    val shadowDisplayController by lazy { createShadowDisplayController(UI_AUDIT_SOURCE) }

    fun createShadowDisplayController(auditSource: String) =
        ShadowDisplayController(privilegeRouter, auditStore, auditSource)

    val agentSessionStore by lazy { AgentSessionStore(application) }
    val agentSessionManager by lazy { AgentSessionManager(application, agentSessionStore) }

    val assistantRepository by lazy { AssistantRepository(application, privilegeRouter) }
    val assistantController by lazy {
        AssistantController(
            repository = assistantRepository,
            privilegeRouter = privilegeRouter,
            auditStore = auditStore,
            auditSource = UI_AUDIT_SOURCE,
        )
    }

    val tokenStore by lazy { ActionTokenStore(application) }
    val reportStore by lazy { DiagnosticReportStore(application) }
    val favoritesStore by lazy { ActionFavoritesStore(application) }

    companion object {
        private const val UI_AUDIT_SOURCE = "UI"
    }
}
