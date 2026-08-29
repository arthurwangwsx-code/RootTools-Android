package com.arthur.roottools.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthur.roottools.R
import com.arthur.roottools.app.RootToolsApp
import com.arthur.roottools.model.AdbBootPolicy
import com.arthur.roottools.model.AdbSnapshot
import com.arthur.roottools.model.AppComponentRecord
import com.arthur.roottools.model.AppPolicyProfileId
import com.arthur.roottools.model.DeviceSnapshot
import com.arthur.roottools.model.FrameworkPrivilegePreference
import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.model.CapabilityProbeResult
import com.arthur.roottools.model.PrivilegeCapability
import com.arthur.roottools.model.StartupAnalysis
import com.arthur.roottools.model.SystemActionId
import com.arthur.roottools.service.CpuPolicyService
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as RootToolsApp).container
    private val shell = container.shell
    private val rootAuthorizationManager = container.rootAuthorizationManager
    private val shizukuBridge = container.shizukuBridge
    private val shizukuUserService = container.shizukuUserService
    private val appControlRepository = container.appControlRepository
    private val appControlDiagnosticStore = container.appControlDiagnosticStore
    private val appRuntimeRepository = container.appRuntimeRepository
    private val appBatchPolicyEngine = container.appBatchPolicyEngine
    private val componentRepository = container.componentRepository
    private val permissionAppOpsRepository = container.permissionAppOpsRepository
    private val auditStore = container.auditStore
    private val store = container.policyStore
    private val policyEventStore = container.policyEventStore
    private val policyInspector = container.policyInspector
    private val cpuPolicyController = container.cpuPolicyController
    private val repository = container.deviceRepository
    private val adbRepository = container.adbRepository
    private val adbController = container.adbController
    private val sampler = container.sampler
    private val startupRepository = container.startupRepository
    private val diagnosticsRepository = container.diagnosticsRepository
    private val moduleCenterRepository = container.moduleCenterRepository
    private val networkRepository = container.networkRepository
    private val storageRepository = container.storageRepository
    private val batteryPolicyController = container.batteryPolicyController
    private val systemActionController = container.systemActionController
    private val tokenStore = container.tokenStore
    private val reportStore = container.reportStore
    private val packagePolicyController = container.packagePolicyController
    private val componentPolicyController = container.componentPolicyController
    private val appOpsPolicyController = container.appOpsPolicyController
    private val permissionPolicyController = container.permissionPolicyController
    private val favoritesStore = container.favoritesStore
    private val privilegePreferenceStore = container.privilegePreferenceStore
    private val _state = MutableStateFlow(
        DashboardUiState(
            mode = store.mode,
            detailSamplingSeconds = sampler.detailIntervalSeconds(),
            automationToken = tokenStore.token,
            favoriteActions = favoritesStore.read(),
            componentCatalog = componentRepository.installedUserPackages(),
            appBatchLastApplied = appBatchPolicyEngine.lastApplied(),
            cpuPolicyEvents = policyEventStore.read(30),
            auditRecords = auditStore.read(30),
            rootAuthorization = rootAuthorizationManager.state.value,
            frameworkPrivilegePreference = privilegePreferenceStore.frameworkPreference,
        )
    )
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()
    private var bootstrapStarted = false
    private var appForeground = false
    private var dashboardSamplingActive = false

    init {
        viewModelScope.launch {
            rootAuthorizationManager.state.collect { authorization ->
                _state.update { it.copy(rootAuthorization = authorization) }
            }
        }
        viewModelScope.launch {
            shizukuBridge.state.collect { bridgeState ->
                _state.update { it.copy(shizuku = bridgeState) }
            }
        }
        viewModelScope.launch {
            sampler.snapshot.collect { health ->
                _state.update {
                    val mergedSnapshot = mergeHealthIntoSnapshot(it.snapshot, health)
                    val capChanged = it.snapshot.cpuClusters.map { cluster -> cluster.policyId to cluster.scalingMaxKHz } !=
                        mergedSnapshot.cpuClusters.map { cluster -> cluster.policyId to cluster.scalingMaxKHz }
                    it.copy(
                        snapshot = mergedSnapshot,
                        health = health,
                        healthHistory = sampler.historySnapshot(),
                        dailyHealthHistory = sampler.dailyHistorySnapshot(),
                        cpuCapStates = policyInspector.inspect(mergedSnapshot),
                        cpuPolicyEvents = if (capChanged) policyEventStore.read(30) else it.cpuPolicyEvents,
                    )
                }
            }
        }
    }

    fun bootstrap() {
        if (bootstrapStarted) return
        bootstrapStarted = true
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notificationsGranted = notificationsGranted(getApplication())) }
            val rootGranted = rootAuthorizationManager.request(timeoutSeconds = 60).granted
            val adb = if (rootGranted) adbRepository.read() else AdbSnapshot()
            val snapshot = if (rootGranted) mergeAdbIntoSnapshot(repository.readSnapshot(), adb) else DeviceSnapshot(rootAvailable = false)
            if (rootGranted) {
                CpuPolicyService.ensureRunning(getApplication())
            }
            if (rootGranted && appForeground) sampler.start(viewModelScope, dashboardSamplingActive)
            _state.update {
                it.copy(
                    loading = false,
                    snapshot = snapshot,
                    adb = adb,
                    mode = store.mode,
                    cpuCapStates = policyInspector.inspect(snapshot),
                    cpuPolicyEvents = policyEventStore.read(30),
                    auditRecords = auditStore.read(30),
                    notificationsGranted = notificationsGranted(getApplication()),
                    error = null,
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val adb = adbRepository.read()
            val snapshot = mergeAdbIntoSnapshot(repository.readSnapshot(), adb)
            if (snapshot.rootAvailable && appForeground && !sampler.snapshot.value.rootAvailable) {
                sampler.start(viewModelScope, dashboardSamplingActive)
            }
            _state.update {
                it.copy(
                    loading = false,
                    snapshot = snapshot,
                    adb = adb,
                    mode = store.mode,
                    cpuCapStates = policyInspector.inspect(snapshot),
                    cpuPolicyEvents = policyEventStore.read(30),
                    auditRecords = auditStore.read(30),
                    notificationsGranted = notificationsGranted(getApplication()),
                    error = null,
                )
            }
        }
    }

    fun requestRoot() {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, error = null) }
            val rootGranted = rootAuthorizationManager.request(timeoutSeconds = 60).granted
            val adb = if (rootGranted) adbRepository.read() else AdbSnapshot()
            val snapshot = if (rootGranted) mergeAdbIntoSnapshot(repository.readSnapshot(), adb) else DeviceSnapshot(rootAvailable = false)
            if (rootGranted) {
                CpuPolicyService.ensureRunning(getApplication())
            }
            if (rootGranted && appForeground) sampler.start(viewModelScope, dashboardSamplingActive)
            _state.update {
                it.copy(
                    actionInProgress = false,
                    snapshot = snapshot,
                    adb = adb,
                    cpuCapStates = policyInspector.inspect(snapshot),
                    cpuPolicyEvents = policyEventStore.read(30),
                    auditRecords = auditStore.read(30),
                    notificationsGranted = notificationsGranted(getApplication()),
                    error = if (!rootGranted) "Root 授权未通过，请在 Magisk 中允许 Root Tools" else null,
                )
            }
        }
    }

    fun setFrameworkPrivilegePreference(preference: FrameworkPrivilegePreference) {
        privilegePreferenceStore.frameworkPreference = preference
        _state.update { it.copy(frameworkPrivilegePreference = preference) }
    }

    fun setMode(mode: PerformanceMode) {
        _state.update { it.copy(mode = mode, actionInProgress = true, error = null) }
        CpuPolicyService.setMode(getApplication(), mode, source = "UI")
        viewModelScope.launch {
            delay(900)
            val snapshot = repository.readSnapshot()
            _state.update {
                it.copy(
                    snapshot = snapshot,
                    actionInProgress = false,
                    mode = store.mode,
                    cpuCapStates = policyInspector.inspect(snapshot),
                    cpuPolicyEvents = policyEventStore.read(30),
                    auditRecords = auditStore.read(30),
                )
            }
        }
    }

    fun toggleAdb() {
        val enable = !_state.value.adb.rootTcpEnabled
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, error = null) }
            val result = adbController.setRootTcpEnabled(enable)
            val adb = result.snapshot ?: adbRepository.read()
            val snapshot = mergeAdbIntoSnapshot(repository.readSnapshot(), adb)
            _state.update {
                it.copy(
                    actionInProgress = false,
                    snapshot = snapshot,
                    adb = adb,
                    auditRecords = auditStore.read(30),
                    actionMessage = if (result.success) result.message else null,
                    error = if (!result.success) result.message else null,
                )
            }
        }
    }

    fun loadAdb() {
        if (_state.value.adbLoading) return
        viewModelScope.launch {
            _state.update { it.copy(adbLoading = true, actionMessage = null, error = null) }
            val adb = adbRepository.read()
            _state.update {
                it.copy(
                    adb = adb,
                    adbLoading = false,
                    snapshot = mergeAdbIntoSnapshot(it.snapshot, adb),
                    error = if (!adb.rootAvailable) "ADB Control Center 需要 Root 权限" else null,
                )
            }
        }
    }

    fun setNativeWireless(enabled: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, actionMessage = null, error = null) }
            val result = adbController.setNativeWirelessEnabled(enabled)
            val adb = result.snapshot ?: adbRepository.read()
            _state.update {
                it.copy(
                    actionInProgress = false,
                    adb = adb,
                    snapshot = mergeAdbIntoSnapshot(it.snapshot, adb),
                    auditRecords = auditStore.read(30),
                    actionMessage = if (result.success) result.message else null,
                    error = if (result.success) null else result.message,
                )
            }
        }
    }

    fun setAdbBootPolicy(restoreRootTcp: Boolean? = null, restoreNativeWireless: Boolean? = null) {
        val current = _state.value.adb.bootPolicy
        val policy = AdbBootPolicy(
            restoreRootTcp = restoreRootTcp ?: current.restoreRootTcp,
            restoreNativeWireless = restoreNativeWireless ?: current.restoreNativeWireless,
        )
        adbController.setBootPolicy(policy)
        _state.update {
            it.copy(
                adb = it.adb.copy(bootPolicy = policy),
                actionMessage = "ADB 开机恢复策略已保存",
                error = null,
            )
        }
    }

    fun setDashboardSampling(active: Boolean) {
        dashboardSamplingActive = active
        if (!_state.value.snapshot.rootAvailable) return
        if (!appForeground) return
        sampler.setDashboardActive(viewModelScope, active)
    }

    fun setAppForeground(foreground: Boolean) {
        if (appForeground == foreground) return
        appForeground = foreground
        if (!foreground) {
            sampler.stop()
            return
        }
        if (_state.value.snapshot.rootAvailable) {
            sampler.start(viewModelScope, dashboardSamplingActive)
        }
    }

    fun setDetailSamplingSeconds(seconds: Int) {
        if (seconds !in com.arthur.roottools.data.SamplingPreferenceStore.ALLOWED_SECONDS) return
        sampler.setDetailIntervalSeconds(viewModelScope, seconds)
        _state.update { it.copy(detailSamplingSeconds = seconds) }
    }

    fun loadPerformanceExplain() {
        val snapshot = _state.value.snapshot
        _state.update {
            it.copy(
                cpuCapStates = policyInspector.inspect(snapshot),
                cpuPolicyEvents = policyEventStore.read(30),
                auditRecords = auditStore.read(30),
            )
        }
    }

    fun releaseRootToolsCpuCaps() {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, actionMessage = null, error = null) }
            val before = repository.readSnapshot()
            val success = cpuPolicyController.releaseOwnedCaps(before)
            delay(500)
            val after = repository.readSnapshot()
            _state.update {
                it.copy(
                    actionInProgress = false,
                    snapshot = after,
                    cpuCapStates = policyInspector.inspect(after),
                    cpuPolicyEvents = policyEventStore.read(30),
                    auditRecords = auditStore.read(30),
                    actionMessage = if (success) "已处理 Root Tools 可安全释放的 CPU cap" else null,
                    error = if (success) null else "当前不是 Normal 热状态，或没有可安全释放的 Root Tools cap",
                )
            }
        }
    }

    fun loadAudit() {
        _state.update { it.copy(auditRecords = auditStore.read(50)) }
    }

    fun refreshShizuku() {
        shizukuBridge.refresh()
    }

    fun requestShizukuPermission() {
        val requested = shizukuBridge.requestPermission()
        if (!requested) {
            _state.update {
                it.copy(
                    actionMessage = null,
                    error = when {
                        it.shizuku.permissionGranted -> null
                        !it.shizuku.binderAlive -> "Shizuku / Sui Binder 当前不可用"
                        it.shizuku.permissionDeniedPermanently -> "Shizuku 权限已被拒绝，请在 Shizuku Manager 中重新授权"
                        else -> "无法发起 Shizuku 权限请求"
                    },
                )
            }
        }
    }

    fun runShizukuSelfTest() {
        if (_state.value.shizukuSelfTestLoading) return
        viewModelScope.launch {
            val bridgeState = _state.value.shizuku
            if (!bridgeState.ready) {
                _state.update { it.copy(error = "Shizuku / Sui 尚未 Ready，无法运行 Self-test") }
                return@launch
            }
            _state.update { it.copy(shizukuSelfTestLoading = true, error = null, actionMessage = null) }
            val backend = bridgeState.backend
            val startNs = System.nanoTime()
            val uidResult = shizukuUserService.call { it.backendUid }
            val latencyMs = (System.nanoTime() - startNs) / 1_000_000.0
            val packageResult = shizukuUserService.call { it.packageExists(getApplication<Application>().packageName) }
            val activityResult = shizukuUserService.call { it.topPackage }
            val appOpsResult = shizukuUserService.call { it.getAppOp(getApplication<Application>().packageName, "RUN_IN_BACKGROUND") }
            val frameworkResult = shizukuUserService.selfTest()

            val results = listOf(
                CapabilityProbeResult(
                    capability = PrivilegeCapability.FRAMEWORK_DIAGNOSTICS,
                    available = uidResult.isSuccess,
                    backend = backend,
                    detail = uidResult.getOrNull()?.let { "UserService UID $it" } ?: uidResult.exceptionOrNull()?.message.orEmpty(),
                    latencyMs = if (uidResult.isSuccess) latencyMs else null,
                ),
                CapabilityProbeResult(
                    capability = PrivilegeCapability.PACKAGE_CONTROL,
                    available = packageResult.getOrDefault(false),
                    backend = backend,
                    detail = if (packageResult.getOrDefault(false)) "PackageManager command path reachable" else "Package read failed",
                ),
                CapabilityProbeResult(
                    capability = PrivilegeCapability.COMPONENT_CONTROL,
                    available = packageResult.isSuccess,
                    backend = backend,
                    detail = "Typed component gateway ${if (packageResult.isSuccess) "available" else "unavailable"}",
                ),
                CapabilityProbeResult(
                    capability = PrivilegeCapability.ACTIVITY_CONTROL,
                    available = activityResult.isSuccess,
                    backend = backend,
                    detail = activityResult.getOrNull()?.ifBlank { "Activity service reachable" } ?: "Activity service failed",
                ),
                CapabilityProbeResult(
                    capability = PrivilegeCapability.APP_OPS,
                    available = appOpsResult.isSuccess,
                    backend = backend,
                    detail = appOpsResult.getOrNull()?.take(160).orEmpty().ifBlank { if (appOpsResult.isSuccess) "AppOps reachable" else "AppOps failed" },
                ),
                CapabilityProbeResult(
                    capability = PrivilegeCapability.ROOT_LINUX,
                    available = bridgeState.uid == 0,
                    backend = backend,
                    detail = if (bridgeState.uid == 0) "Remote identity is root; RootTools still prefers RootShell for Linux/sysfs" else "Remote identity is shell",
                ),
            )
            _state.update {
                it.copy(
                    shizukuSelfTest = results,
                    shizukuSelfTestLoading = false,
                    actionMessage = frameworkResult.getOrNull()?.let { text -> "Self-test: $text" },
                    error = frameworkResult.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun loadComponents(packageName: String) {
        if (_state.value.componentLoading) return
        viewModelScope.launch(Dispatchers.Default) {
            _state.update { it.copy(componentLoading = true, actionMessage = null, error = null) }
            val snapshot = componentRepository.read(packageName.trim())
            _state.update {
                it.copy(
                    componentSnapshot = snapshot,
                    componentLoading = false,
                    error = if (snapshot == null) "未找到应用或 package name 无效" else null,
                )
            }
        }
    }

    fun setComponentEnabled(component: AppComponentRecord, enabled: Boolean) {
        val current = _state.value.componentSnapshot ?: return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, actionMessage = null, error = null) }
            val result = componentPolicyController.setEnabled(current, component, enabled)
            val refreshed = componentRepository.read(current.packageName)
            val verified = refreshed?.components?.firstOrNull { it.componentName == component.componentName }?.enabled == enabled
            _state.update {
                it.copy(
                    actionInProgress = false,
                    componentSnapshot = refreshed ?: current,
                    auditRecords = auditStore.read(30),
                    actionMessage = if (result.success && verified) "${result.message} · verified" else null,
                    error = when {
                        !result.success -> result.message
                        !verified -> "组件命令返回成功，但重新读取状态未验证通过"
                        else -> null
                    },
                )
            }
        }
    }

    fun launchComponent(component: AppComponentRecord) {
        val current = _state.value.componentSnapshot ?: return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, actionMessage = null, error = null) }
            val result = componentPolicyController.launch(current, component)
            _state.update {
                it.copy(
                    actionInProgress = false,
                    actionMessage = if (result.success) result.message else null,
                    error = if (result.success) null else result.message,
                )
            }
        }
    }

    fun loadPermissionAppOps(packageName: String) {
        if (_state.value.permissionOpsLoading) return
        viewModelScope.launch {
            _state.update { it.copy(permissionOpsLoading = true, actionMessage = null, error = null) }
            val canAppOps = _state.value.snapshot.rootAvailable || _state.value.shizuku.ready
            val snapshot = permissionAppOpsRepository.read(packageName.trim(), includeAppOps = canAppOps)
            _state.update {
                it.copy(
                    permissionOpsSnapshot = snapshot,
                    permissionOpsLoading = false,
                    error = if (snapshot == null) "未找到应用或 package name 无效" else null,
                )
            }
        }
    }

    fun setAppOpMode(op: String, mode: String) {
        val current = _state.value.permissionOpsSnapshot ?: return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, actionMessage = null, error = null) }
            val result = appOpsPolicyController.setMode(current.packageName, op, mode)
            val refreshed = permissionAppOpsRepository.read(
                current.packageName,
                includeAppOps = _state.value.snapshot.rootAvailable || _state.value.shizuku.ready,
            )
            _state.update {
                it.copy(
                    actionInProgress = false,
                    permissionOpsSnapshot = refreshed ?: current,
                    auditRecords = auditStore.read(30),
                    actionMessage = if (result.success) result.message else null,
                    error = if (result.success) null else result.message,
                )
            }
        }
    }

    fun setRuntimePermission(permissionName: String, granted: Boolean) {
        val current = _state.value.permissionOpsSnapshot ?: return
        val permission = current.permissions.firstOrNull { it.name == permissionName } ?: return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, actionMessage = null, error = null) }
            val result = permissionPolicyController.setGranted(current.packageName, permission, granted)
            val refreshed = permissionAppOpsRepository.read(
                current.packageName,
                includeAppOps = current.appOpsBackendAvailable,
            )
            val verified = refreshed?.permissions?.firstOrNull { it.name == permissionName }?.granted == granted
            _state.update {
                it.copy(
                    actionInProgress = false,
                    permissionOpsSnapshot = refreshed ?: current,
                    auditRecords = auditStore.read(30),
                    actionMessage = if (result.success && verified) "${result.message} · verified" else null,
                    error = when {
                        !result.success -> result.message
                        !verified -> "Permission 命令返回成功，但重新读取状态未验证通过"
                        else -> null
                    },
                )
            }
        }
    }

    fun exportAppControlDiagnostic() {
        val detail = _state.value.appControlDetail ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(actionInProgress = true, actionMessage = null, error = null) }
            val components = _state.value.componentSnapshot?.takeIf { it.packageName == detail.packageName }
                ?: componentRepository.read(detail.packageName)
            val permissionOps = permissionAppOpsRepository.read(
                detail.packageName,
                includeAppOps = _state.value.snapshot.rootAvailable || _state.value.shizuku.ready,
            )
            val result = runCatching { appControlDiagnosticStore.write(detail, components, permissionOps) }
            _state.update {
                it.copy(
                    actionInProgress = false,
                    appControlExport = result.getOrNull(),
                    permissionOpsSnapshot = permissionOps ?: it.permissionOpsSnapshot,
                    actionMessage = result.getOrNull()?.let { export -> "已导出单应用诊断：${export.markdownPath}" },
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun loadAppRuntime(packageName: String) {
        if (_state.value.appRuntimeLoading) return
        viewModelScope.launch {
            _state.update { it.copy(appRuntimeLoading = true, error = null) }
            val runtime = appRuntimeRepository.read(packageName)
            _state.update {
                it.copy(
                    appRuntime = runtime,
                    appRuntimeLoading = false,
                    error = if (runtime == null) "未读取到 Runtime snapshot；需要 Root 或 Shizuku/Sui" else null,
                )
            }
        }
    }

    fun toggleAppBatchSelection(packageName: String) {
        _state.update { state ->
            val next = if (packageName in state.appBatchSelection) state.appBatchSelection - packageName else state.appBatchSelection + packageName
            state.copy(appBatchSelection = next, appBatchPlan = null)
        }
    }

    fun clearAppBatchSelection() {
        _state.update { it.copy(appBatchSelection = emptySet(), appBatchPlan = null) }
    }

    fun setAppBatchProfile(profile: AppPolicyProfileId) {
        _state.update { it.copy(appBatchProfile = profile, appBatchPlan = null) }
    }

    fun previewAppBatchPlan() {
        val selected = _state.value.appBatchSelection
        if (selected.isEmpty()) return
        val profile = _state.value.appBatchProfile
        viewModelScope.launch {
            _state.update { it.copy(appBatchLoading = true, actionMessage = null, error = null) }
            val plan = appBatchPolicyEngine.buildPlan(selected, profile)
            _state.update { it.copy(appBatchPlan = plan, appBatchLoading = false) }
        }
    }

    fun applyAppBatchPlan() {
        val plan = _state.value.appBatchPlan ?: return
        viewModelScope.launch {
            _state.update { it.copy(appBatchLoading = true, actionMessage = null, error = null) }
            val result = appBatchPolicyEngine.apply(plan)
            val inventory = appControlRepository.readInventory()
            _state.update {
                it.copy(
                    appInventory = inventory,
                    appBatchPlan = result.plan,
                    appBatchLastApplied = if (result.success) result.plan else appBatchPolicyEngine.lastApplied(),
                    appBatchLoading = false,
                    auditRecords = auditStore.read(50),
                    actionMessage = if (result.success) result.message else null,
                    error = if (result.success) null else result.message,
                )
            }
        }
    }

    fun rollbackLastAppBatchPlan() {
        val plan = _state.value.appBatchLastApplied ?: return
        viewModelScope.launch {
            _state.update { it.copy(appBatchLoading = true, actionMessage = null, error = null) }
            val result = appBatchPolicyEngine.rollback(plan)
            val inventory = appControlRepository.readInventory()
            _state.update {
                it.copy(
                    appInventory = inventory,
                    appBatchLastApplied = if (result.success) null else plan,
                    appBatchPlan = result.plan,
                    appBatchLoading = false,
                    auditRecords = auditStore.read(50),
                    actionMessage = if (result.success) result.message else null,
                    error = if (result.success) null else result.message,
                )
            }
        }
    }

    fun loadStartup() {
        if (_state.value.startupLoading) return
        viewModelScope.launch {
            _state.update { it.copy(startupLoading = true, actionMessage = null, error = null) }
            if (!_state.value.snapshot.rootAvailable) {
                _state.update {
                    it.copy(
                        startup = StartupAnalysis(
                            degradedMode = true,
                            source = "Root required for boot event trace",
                        ),
                        startupLoading = false,
                        error = "启动时间线需要 Root；Shizuku-only 模式仍可使用应用治理 / 组件 / AppOps",
                    )
                }
                return@launch
            }
            val analysis = startupRepository.analyzeCurrentBoot()
            _state.update {
                it.copy(
                    startup = analysis,
                    startupLoading = false,
                    error = if (analysis.apps.isEmpty()) "启动分析未读取到数据，请确认 Root / logcat 权限" else null,
                )
            }
        }
    }

    fun loadAppControl() {
        if (_state.value.appInventoryLoading) return
        viewModelScope.launch(Dispatchers.Default) {
            _state.update { it.copy(appInventoryLoading = true, actionMessage = null, error = null) }
            val inventory = appControlRepository.readInventory(allowRootRunningProbe = _state.value.snapshot.rootAvailable)
            _state.update {
                it.copy(
                    appInventory = inventory,
                    appInventoryLoading = false,
                    error = if (inventory.apps.isEmpty()) getApplication<Application>().getString(R.string.app_control_error_inventory_empty) else null,
                )
            }
        }
    }

    fun loadAppControlDetail(packageName: String) {
        val pkg = packageName.trim()
        if (_state.value.appControlDetailLoading) return
        viewModelScope.launch(Dispatchers.Default) {
            _state.update {
                it.copy(
                    appControlDetailLoading = true,
                    appControlExport = null,
                    appRuntime = null,
                    componentSnapshot = null,
                    permissionOpsSnapshot = null,
                    actionMessage = null,
                    error = null,
                )
            }
            val detail = appControlRepository.readDetail(pkg)
            val components = componentRepository.read(pkg)
            val permissionOps = permissionAppOpsRepository.read(pkg, includeAppOps = false)
            _state.update {
                it.copy(
                    appControlDetail = detail,
                    appControlDetailLoading = false,
                    componentSnapshot = components,
                    permissionOpsSnapshot = permissionOps,
                    error = if (detail == null) getApplication<Application>().getString(R.string.app_control_error_package_not_found) else null,
                )
            }
        }
    }

    fun clearAppControlDetail() {
        _state.update {
            it.copy(
                appControlDetail = null,
                appControlExport = null,
                appRuntime = null,
                componentSnapshot = null,
                permissionOpsSnapshot = null,
                actionMessage = null,
                error = null,
            )
        }
    }

    fun freezePackage(packageName: String) = runPackageAction { packagePolicyController.freeze(packageName) }

    fun enablePackage(packageName: String) = runPackageAction { packagePolicyController.enable(packageName) }

    fun forceStopPackage(packageName: String) = runPackageAction { packagePolicyController.forceStop(packageName) }

    fun setPackageBucket(packageName: String, bucket: Int) = runPackageAction { packagePolicyController.setStandbyBucket(packageName, bucket) }

    fun setPackageBackground(packageName: String, allowed: Boolean) = runPackageAction {
        packagePolicyController.setBackgroundAllowed(packageName, allowed)
    }

    fun setAppiumTestMode(enabled: Boolean) = runPackageAction { packagePolicyController.setAppiumTestMode(enabled) }

    fun loadDiagnostics() {
        if (_state.value.diagnosticsLoading) return
        viewModelScope.launch {
            _state.update { it.copy(diagnosticsLoading = true, actionMessage = null, error = null) }
            val diagnostics = diagnosticsRepository.collect()
            val text = diagnosticsRepository.buildSnapshotText(_state.value.health, diagnostics)
            _state.update {
                it.copy(
                    diagnostics = diagnostics,
                    diagnosticsLoading = false,
                    diagnosticText = text,
                    error = if (diagnostics.topProcesses.isEmpty()) "诊断未读取到进程数据，请确认 Root 权限" else null,
                )
            }
        }
    }

    fun attributeRootShell(pid: Int) {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, actionMessage = "正在扫描 pipe owner…", error = null) }
            val details = diagnosticsRepository.attributeRootShell(pid)
            _state.update {
                it.copy(
                    actionInProgress = false,
                    rootShellDetails = it.rootShellDetails + (pid to details),
                    actionMessage = if (details.attributions.isEmpty()) "未找到共享 pipe 的应用进程" else "已找到 ${details.attributions.size} 个 pipe owner",
                )
            }
        }
    }

    fun loadModules() {
        if (_state.value.modulesLoading) return
        viewModelScope.launch {
            _state.update { it.copy(modulesLoading = true, actionMessage = null, error = null) }
            val snapshot = moduleCenterRepository.read()
            _state.update {
                it.copy(
                    modules = snapshot,
                    modulesLoading = false,
                    error = if (snapshot.magiskModules.isEmpty() && snapshot.vectorModules.isEmpty()) "未读取到 Root 模块信息" else null,
                )
            }
        }
    }

    fun loadVectorScope(packageName: String) {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, actionMessage = null, error = null) }
            val scope = moduleCenterRepository.readScope(packageName)
            _state.update {
                it.copy(
                    actionInProgress = false,
                    modules = it.modules.copy(scopes = it.modules.scopes + (packageName to scope)),
                    actionMessage = "已读取 ${scope.size} 个 Scope",
                )
            }
        }
    }

    fun setMagiskModuleEnabled(moduleId: String, enabled: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, actionMessage = null, error = null) }
            val result = moduleCenterRepository.setMagiskEnabled(moduleId, enabled)
            val snapshot = moduleCenterRepository.read()
            _state.update {
                it.copy(
                    actionInProgress = false,
                    modules = snapshot.copy(scopes = it.modules.scopes),
                    pendingRebootModules = if (result.success) it.pendingRebootModules + moduleId else it.pendingRebootModules,
                    auditRecords = auditStore.read(30),
                    actionMessage = if (result.success) result.message else null,
                    error = if (result.success) null else result.message,
                )
            }
        }
    }

    fun setVectorModuleEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, actionMessage = null, error = null) }
            val result = moduleCenterRepository.setVectorEnabled(packageName, enabled)
            val snapshot = moduleCenterRepository.read()
            _state.update {
                it.copy(
                    actionInProgress = false,
                    modules = snapshot.copy(scopes = it.modules.scopes),
                    auditRecords = auditStore.read(30),
                    actionMessage = if (result.success) result.message else null,
                    error = if (result.success) null else result.message,
                )
            }
        }
    }

    fun runSystemAction(action: SystemActionId) {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, actionMessage = null, error = null) }
            val result = systemActionController.run(action)
            _state.update {
                it.copy(
                    actionInProgress = false,
                    auditRecords = auditStore.read(30),
                    actionMessage = if (result.success) result.message else null,
                    error = if (result.success) null else result.message,
                )
            }
            if (action == SystemActionId.BATTERY_PROTECTION_80 || action == SystemActionId.STOP_BILIBILI) refresh()
        }
    }

    fun exportDiagnosticReport() {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, actionMessage = null, error = null) }
            val diagnostics = if (_state.value.diagnostics.topProcesses.isEmpty()) diagnosticsRepository.collect() else _state.value.diagnostics
            val current = _state.value
            val base = diagnosticsRepository.buildSnapshotText(current.health, diagnostics)
            val shizukuSection = buildString {
                appendLine()
                appendLine("== Shizuku / Sui ==")
                appendLine("binder=${current.shizuku.binderAlive}")
                appendLine("permission=${current.shizuku.permissionGranted}")
                appendLine("backend=${current.shizuku.backend.displayName}")
                appendLine("uid=${current.shizuku.uid ?: -1}")
                appendLine("server=${current.shizuku.serverVersion ?: -1}.${current.shizuku.serverPatchVersion ?: -1}")
                appendLine("sui=${current.shizuku.suiAvailable}")
                current.shizukuSelfTest.forEach { probe ->
                    appendLine("probe ${probe.capability.name}=${probe.available} backend=${probe.backend.displayName} latency=${probe.latencyMs ?: -1.0} detail=${probe.detail}")
                }
            }
            val text = base + shizukuSection
            val file = runCatching { reportStore.write(text) }.getOrNull()
            _state.update {
                it.copy(
                    actionInProgress = false,
                    diagnostics = diagnostics,
                    diagnosticText = text,
                    lastReportPath = file?.absolutePath,
                    actionMessage = if (file != null) "诊断报告已生成" else null,
                    error = if (file == null) "诊断报告写入失败" else null,
                )
            }
        }
    }

    fun loadNetwork() {
        if (_state.value.networkLoading) return
        viewModelScope.launch {
            _state.update { it.copy(networkLoading = true, actionMessage = null, error = null) }
            val snapshot = networkRepository.read()
            _state.update {
                it.copy(
                    network = snapshot,
                    networkLoading = false,
                    error = if (snapshot.interfaces.isEmpty()) "未读取到网络接口，请检查 Root 权限" else null,
                )
            }
        }
    }

    fun pingNetworkTarget(target: String) {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, actionMessage = null, error = null, pingResult = null) }
            val result = networkRepository.ping(target)
            _state.update {
                it.copy(
                    actionInProgress = false,
                    pingResult = result,
                    actionMessage = if (result.success) "连通性测试完成" else null,
                    error = if (result.success) null else (result.rawSummary.ifBlank { "Ping 失败" }),
                )
            }
        }
    }

    fun loadStorage() {
        if (_state.value.storageLoading) return
        viewModelScope.launch {
            _state.update { it.copy(storageLoading = true, actionMessage = null, error = null) }
            val snapshot = storageRepository.read()
            _state.update {
                it.copy(
                    storage = snapshot,
                    storageLoading = false,
                    error = if (snapshot.fileSystems.isEmpty()) "未读取到存储状态" else null,
                )
            }
        }
    }

    fun setBatteryProtection(enabled: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, actionMessage = null, error = null) }
            val result = batteryPolicyController.setProtection(enabled, 80)
            val health = if (result.success) com.arthur.roottools.data.DeviceHealthCollector(shell).collect(includeProcesses = false) else _state.value.health
            _state.update {
                it.copy(
                    actionInProgress = false,
                    health = health,
                    auditRecords = auditStore.read(30),
                    actionMessage = if (result.success) result.message else null,
                    error = if (result.success) null else result.message,
                )
            }
        }
    }

    fun setActionFavorite(action: SystemActionId, favorite: Boolean) {
        _state.update { it.copy(favoriteActions = favoritesStore.set(action, favorite)) }
    }

    private fun runPackageAction(action: suspend () -> com.arthur.roottools.model.PackageActionResult) {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, actionMessage = null, error = null) }
            val result = action()
            val analysis = if (_state.value.snapshot.rootAvailable) startupRepository.analyzeCurrentBoot() else _state.value.startup
            val inventory = if (_state.value.appInventory.apps.isNotEmpty()) {
                appControlRepository.readInventory(allowRootRunningProbe = _state.value.snapshot.rootAvailable)
            } else {
                _state.value.appInventory
            }
            val detailPackage = _state.value.appControlDetail?.packageName
            val detail = detailPackage?.let(appControlRepository::readDetail)
            val components = detailPackage?.let(componentRepository::read)
            val hadAppOpsLoaded = _state.value.permissionOpsSnapshot?.appOps?.isNotEmpty() == true
            val permissionOps = detailPackage?.let { packageName ->
                permissionAppOpsRepository.read(
                    packageName,
                    includeAppOps = hadAppOpsLoaded && (_state.value.snapshot.rootAvailable || _state.value.shizuku.ready),
                )
            }
            _state.update {
                it.copy(
                    actionInProgress = false,
                    startup = analysis,
                    appInventory = inventory,
                    appControlDetail = detail ?: it.appControlDetail,
                    componentSnapshot = components ?: it.componentSnapshot,
                    permissionOpsSnapshot = permissionOps ?: it.permissionOpsSnapshot,
                    auditRecords = auditStore.read(30),
                    actionMessage = if (result.success) result.message else null,
                    error = if (result.success) null else result.message,
                )
            }
        }
    }

    override fun onCleared() {
        sampler.stop()
        shizukuUserService.close()
        shizukuBridge.close()
        super.onCleared()
    }

}

