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
import com.arthur.roottools.data.ModuleCenterRepository
import com.arthur.roottools.data.NetworkRepository
import com.arthur.roottools.data.PermissionAppOpsRepository
import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.data.StartupRepository
import com.arthur.roottools.data.StorageRepository
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
import com.arthur.roottools.privilege.ShizukuBridge
import com.arthur.roottools.privilege.ShizukuUserServiceClient
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
    val shizukuBridge by lazy { ShizukuBridge(application) }
    val shizukuUserService by lazy { ShizukuUserServiceClient(application) }
    val privilegeRouter by lazy { PrivilegeRouter(shizukuBridge, shizukuUserService, shell) }

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

    val deviceRepository by lazy { DeviceRepository(shell) }
    val adbRepository by lazy { AdbRepository(application, shell) }
    val adbController by lazy { createAdbController(UI_AUDIT_SOURCE) }

    fun createAdbController(auditSource: String) =
        AdbController(application, shell, auditStore, auditSource)
    val sampler by lazy { DeviceSamplerService(application) }
    val startupRepository by lazy { StartupRepository(application, shell) }
    val diagnosticsRepository by lazy { DiagnosticsRepository(shell) }
    val moduleCenterRepository by lazy { ModuleCenterRepository(shell, auditStore, UI_AUDIT_SOURCE) }
    val networkRepository by lazy { NetworkRepository(shell) }
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
    val shadowDisplayController by lazy {
        ShadowDisplayController(privilegeRouter, auditStore, UI_AUDIT_SOURCE)
    }

    val tokenStore by lazy { ActionTokenStore(application) }
    val reportStore by lazy { DiagnosticReportStore(application) }
    val favoritesStore by lazy { ActionFavoritesStore(application) }

    companion object {
        private const val UI_AUDIT_SOURCE = "UI"
    }
}
