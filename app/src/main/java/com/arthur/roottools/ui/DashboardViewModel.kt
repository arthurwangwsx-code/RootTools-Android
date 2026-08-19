package com.arthur.roottools.ui

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthur.roottools.automation.ActionTokenStore
import com.arthur.roottools.data.DiagnosticReportStore
import com.arthur.roottools.data.DeviceSamplerService
import com.arthur.roottools.data.DeviceRepository
import com.arthur.roottools.data.DiagnosticsRepository
import com.arthur.roottools.data.FrameworkAppCatalogRepository
import com.arthur.roottools.data.ModuleCenterRepository
import com.arthur.roottools.data.NetworkRepository
import com.arthur.roottools.data.ComponentRepository
import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.data.StorageRepository
import com.arthur.roottools.data.StartupRepository
import com.arthur.roottools.model.DeviceHealthSnapshot
import com.arthur.roottools.model.DeviceSnapshot
import com.arthur.roottools.model.CpuCapState
import com.arthur.roottools.model.CpuPolicyEvent
import com.arthur.roottools.model.CapabilityProbeResult
import com.arthur.roottools.model.ComponentSnapshot
import com.arthur.roottools.model.DiagnosticsSnapshot
import com.arthur.roottools.model.HealthHistoryPoint
import com.arthur.roottools.model.ModuleCenterSnapshot
import com.arthur.roottools.model.NetworkSnapshot
import com.arthur.roottools.model.PingResult
import com.arthur.roottools.model.PrivilegeCapability
import com.arthur.roottools.model.PackageCatalogItem
import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.model.StartupAnalysis
import com.arthur.roottools.model.RootShellDetails
import com.arthur.roottools.model.RootActionAuditRecord
import com.arthur.roottools.model.ShizukuBridgeState
import com.arthur.roottools.model.StorageSnapshot
import com.arthur.roottools.model.SystemActionId
import com.arthur.roottools.policy.PackagePolicyController
import com.arthur.roottools.policy.BatteryPolicyController
import com.arthur.roottools.policy.ActionFavoritesStore
import com.arthur.roottools.policy.CpuPolicyController
import com.arthur.roottools.policy.CpuPolicyEventStore
import com.arthur.roottools.policy.CpuPolicyInspector
import com.arthur.roottools.policy.ComponentPolicyController
import com.arthur.roottools.policy.PolicyStore
import com.arthur.roottools.policy.SystemActionController
import com.arthur.roottools.root.RootShell
import com.arthur.roottools.privilege.PrivilegeRouter
import com.arthur.roottools.privilege.ShizukuBridge
import com.arthur.roottools.privilege.ShizukuSelfTestParser
import com.arthur.roottools.privilege.ShizukuUserServiceClient
import com.arthur.roottools.service.CpuPolicyService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val shizuku: ShizukuBridgeState = ShizukuBridgeState(),
    val shizukuProbes: List<CapabilityProbeResult> = emptyList(),
    val shizukuSelfTestRunning: Boolean = false,
    val componentCatalog: List<PackageCatalogItem> = emptyList(),
    val componentCatalogLoading: Boolean = false,
    val componentSnapshot: ComponentSnapshot? = null,
    val componentLoading: Boolean = false,
    val actionMessage: String? = null,
    val error: String? = null,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val shell = RootShell()
    private val shizukuBridge = ShizukuBridge(application)
    private val shizukuUserServiceClient = ShizukuUserServiceClient(application)
    private val privilegeRouter = PrivilegeRouter(shizukuBridge, shizukuUserServiceClient, shell)
    private val auditStore = RootActionAuditStore(application)
    private val store = PolicyStore(application)
    private val policyEventStore = CpuPolicyEventStore(application)
    private val policyInspector = CpuPolicyInspector(store)
    private val cpuPolicyController = CpuPolicyController(
        shell = shell,
        store = store,
        eventStore = policyEventStore,
        auditStore = auditStore,
        auditSource = "UI",
    )
    private val repository = DeviceRepository(shell, auditStore, "UI")
    private val sampler = DeviceSamplerService(application)
    private val startupRepository = StartupRepository(application, shell)
    private val frameworkAppCatalogRepository = FrameworkAppCatalogRepository(application)
    private val componentRepository = ComponentRepository(application)
    private val diagnosticsRepository = DiagnosticsRepository(shell)
    private val moduleCenterRepository = ModuleCenterRepository(shell, auditStore, "UI")
    private val networkRepository = NetworkRepository(shell)
    private val storageRepository = StorageRepository(shell)
    private val batteryPolicyController = BatteryPolicyController(shell, auditStore, "UI")
    private val systemActionController = SystemActionController(
        shell = shell,
        auditStore = auditStore,
        auditSource = "UI",
        batteryController = batteryPolicyController,
    )
    private val tokenStore = ActionTokenStore(application)
    private val reportStore = DiagnosticReportStore(application)
    private val packagePolicyController = PackagePolicyController(privilegeRouter, auditStore, "UI")
    private val componentPolicyController = ComponentPolicyController(privilegeRouter, auditStore, "UI")
    private val favoritesStore = ActionFavoritesStore(application)
    private val _state = MutableStateFlow(
        DashboardUiState(
            mode = store.mode,
            detailSamplingSeconds = sampler.detailIntervalSeconds(),
            automationToken = tokenStore.token,
            favoriteActions = favoritesStore.read(),
            cpuPolicyEvents = policyEventStore.read(30),
            auditRecords = auditStore.read(30),
        )
    )
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()
    private var bootstrapStarted = false
    private var appForeground = false
    private var dashboardSamplingActive = false

    init {
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
            _state.update { it.copy(loading = true, error = null, notificationsGranted = notificationsGranted()) }
            val rootGranted = shell.isAvailable(timeoutSeconds = 30)
            val snapshot = if (rootGranted) repository.readSnapshot() else DeviceSnapshot(rootAvailable = false)
            if (rootGranted && (store.mode == PerformanceMode.AUTO || store.mode == PerformanceMode.PERFORMANCE)) {
                CpuPolicyService.ensureRunning(getApplication())
            }
            if (rootGranted && appForeground) sampler.start(viewModelScope, dashboardSamplingActive)
            _state.update {
                it.copy(
                    loading = false,
                    snapshot = snapshot,
                    mode = store.mode,
                    cpuCapStates = policyInspector.inspect(snapshot),
                    cpuPolicyEvents = policyEventStore.read(30),
                    auditRecords = auditStore.read(30),
                    notificationsGranted = notificationsGranted(),
                    error = if (!rootGranted && !shizukuBridge.state.value.ready) {
                        "Root 权限未通过；Framework 工具仍可在 Shizuku 授权后使用"
                    } else null,
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val snapshot = repository.readSnapshot()
            if (snapshot.rootAvailable && appForeground && !sampler.snapshot.value.rootAvailable) {
                sampler.start(viewModelScope, dashboardSamplingActive)
            }
            _state.update {
                it.copy(
                    loading = false,
                    snapshot = snapshot,
                    mode = store.mode,
                    cpuCapStates = policyInspector.inspect(snapshot),
                    cpuPolicyEvents = policyEventStore.read(30),
                    auditRecords = auditStore.read(30),
                    notificationsGranted = notificationsGranted(),
                    error = if (!snapshot.rootAvailable && !shizukuBridge.state.value.ready) {
                        "Root 不可用；Framework 工具需要 Shizuku / Sui"
                    } else null,
                )
            }
        }
    }

    fun requestRoot() {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, error = null) }
            val rootGranted = shell.isAvailable(timeoutSeconds = 30)
            val snapshot = if (rootGranted) repository.readSnapshot() else DeviceSnapshot(rootAvailable = false)
            if (rootGranted && (store.mode == PerformanceMode.AUTO || store.mode == PerformanceMode.PERFORMANCE)) {
                CpuPolicyService.ensureRunning(getApplication())
            }
            if (rootGranted && appForeground) sampler.start(viewModelScope, dashboardSamplingActive)
            _state.update {
                it.copy(
                    actionInProgress = false,
                    snapshot = snapshot,
                    cpuCapStates = policyInspector.inspect(snapshot),
                    cpuPolicyEvents = policyEventStore.read(30),
                    auditRecords = auditStore.read(30),
                    notificationsGranted = notificationsGranted(),
                    error = if (!rootGranted) "Root 授权未通过，请在 Magisk 中允许 Root Tools" else null,
                )
            }
        }
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
        val enable = !_state.value.snapshot.adbEnabled
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, error = null) }
            val success = repository.setAdbTcpEnabled(enable)
            delay(900)
            val snapshot = repository.readSnapshot()
            _state.update {
                it.copy(
                    actionInProgress = false,
                    snapshot = snapshot,
                    auditRecords = auditStore.read(30),
                    error = if (!success) "ADB 切换失败，请检查 Root 权限" else null,
                )
            }
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
        if (_state.value.shizukuSelfTestRunning) return
        viewModelScope.launch {
            val bridge = shizukuBridge.state.value
            if (!bridge.ready) {
                _state.update { it.copy(error = "请先启动并授权 Shizuku / Sui") }
                return@launch
            }
            _state.update { it.copy(shizukuSelfTestRunning = true, shizukuProbes = emptyList(), actionMessage = null, error = null) }
            val started = System.nanoTime()
            val result = shizukuUserServiceClient.selfTest()
            val latencyMs = (System.nanoTime() - started) / 1_000_000.0
            val parsed = ShizukuSelfTestParser.parse(result.getOrNull().orEmpty())
            val backend = bridge.backend
            val probes = listOf(
                CapabilityProbeResult(
                    capability = PrivilegeCapability.FRAMEWORK_DIAGNOSTICS,
                    available = result.isSuccess && parsed.uid == bridge.uid,
                    backend = backend,
                    detail = if (result.isSuccess) "UserService UID ${parsed.uid ?: "?"}" else result.exceptionOrNull()?.message.orEmpty(),
                    latencyMs = latencyMs,
                ),
                CapabilityProbeResult(
                    capability = PrivilegeCapability.PACKAGE_CONTROL,
                    available = result.isSuccess && parsed.packageControl,
                    backend = backend,
                    detail = "pm=${if (parsed.packageControl) "ok" else "fail"}",
                    latencyMs = latencyMs,
                ),
                CapabilityProbeResult(
                    capability = PrivilegeCapability.ACTIVITY_CONTROL,
                    available = result.isSuccess && parsed.activityControl,
                    backend = backend,
                    detail = "activity=${if (parsed.activityControl) "ok" else "fail"}",
                    latencyMs = latencyMs,
                ),
                CapabilityProbeResult(
                    capability = PrivilegeCapability.APP_OPS,
                    available = result.isSuccess && parsed.appOps,
                    backend = backend,
                    detail = "appops=${if (parsed.appOps) "ok" else "fail"}",
                    latencyMs = latencyMs,
                ),
            )
            _state.update {
                it.copy(
                    shizukuSelfTestRunning = false,
                    shizukuProbes = probes,
                    actionMessage = if (probes.all { probe -> probe.available }) "Shizuku Framework self-test 通过" else null,
                    error = if (probes.all { probe -> probe.available }) null else "Shizuku self-test 有能力不可用",
                )
            }
        }
    }

    fun loadStartup() {
        if (_state.value.startupLoading) return
        viewModelScope.launch {
            _state.update { it.copy(startupLoading = true, actionMessage = null, error = null) }
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

    fun loadApps() {
        if (_state.value.startupLoading) return
        viewModelScope.launch {
            _state.update { it.copy(startupLoading = true, actionMessage = null, error = null) }
            val analysis = readAppsAnalysis()
            _state.update {
                it.copy(
                    startup = analysis,
                    startupLoading = false,
                    error = when {
                        analysis.apps.isNotEmpty() -> null
                        !it.snapshot.rootAvailable && !it.shizuku.ready -> "应用治理需要 Root 或已授权的 Shizuku / Sui"
                        else -> "应用治理未读取到应用数据"
                    },
                )
            }
        }
    }

    fun loadComponentCatalog() {
        if (_state.value.componentCatalogLoading) return
        viewModelScope.launch {
            _state.update { it.copy(componentCatalogLoading = true, actionMessage = null, error = null) }
            val catalog = componentRepository.catalog(includeSystemApps = false)
            _state.update {
                it.copy(
                    componentCatalog = catalog,
                    componentCatalogLoading = false,
                    error = if (catalog.isEmpty()) "未读取到可管理的用户应用" else null,
                )
            }
        }
    }

    fun loadComponents(packageName: String) {
        viewModelScope.launch {
            _state.update { it.copy(componentLoading = true, actionMessage = null, error = null) }
            val snapshot = componentRepository.read(packageName)
            _state.update {
                it.copy(
                    componentSnapshot = snapshot,
                    componentLoading = false,
                    error = if (snapshot == null) "无法读取 $packageName 的组件信息" else null,
                )
            }
        }
    }

    fun closeComponents() {
        _state.update { it.copy(componentSnapshot = null, actionMessage = null, error = null) }
    }

    fun setComponentEnabled(component: com.arthur.roottools.model.AppComponentRecord, enabled: Boolean) {
        val snapshot = _state.value.componentSnapshot ?: return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, actionMessage = null, error = null) }
            val result = componentPolicyController.setEnabled(snapshot, component, enabled)
            val refreshed = if (result.success) componentRepository.read(snapshot.packageName) else snapshot
            _state.update {
                it.copy(
                    actionInProgress = false,
                    componentSnapshot = refreshed,
                    auditRecords = auditStore.read(30),
                    actionMessage = if (result.success) result.message else null,
                    error = if (result.success) null else result.message,
                )
            }
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
            val text = appendPrivilegeDiagnostics(diagnosticsRepository.buildSnapshotText(_state.value.health, diagnostics))
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
            val text = appendPrivilegeDiagnostics(diagnosticsRepository.buildSnapshotText(_state.value.health, diagnostics))
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
            val analysis = readAppsAnalysis()
            _state.update {
                it.copy(
                    actionInProgress = false,
                    startup = analysis,
                    auditRecords = auditStore.read(30),
                    actionMessage = if (result.success) result.message else null,
                    error = if (result.success) null else result.message,
                )
            }
        }
    }

    override fun onCleared() {
        sampler.stop()
        shizukuUserServiceClient.close()
        shizukuBridge.close()
        super.onCleared()
    }

    private fun mergeHealthIntoSnapshot(
        snapshot: DeviceSnapshot,
        health: DeviceHealthSnapshot,
    ): DeviceSnapshot {
        if (!snapshot.rootAvailable || !health.rootAvailable) return snapshot
        val healthByPolicy = health.cpuClusters.associateBy { it.policyId }
        val clusters = snapshot.cpuClusters.map { cluster ->
            val current = healthByPolicy[cluster.policyId] ?: return@map cluster
            cluster.copy(
                scalingMinKHz = current.scalingMinKHz,
                scalingMaxKHz = current.scalingMaxKHz,
                currentKHz = current.currentKHz,
            )
        }
        return snapshot.copy(
            thermalStatus = health.thermal.status,
            apTempC = health.thermal.apC,
            skinTempC = health.thermal.skinC,
            batteryTempC = health.thermal.batteryC,
            batteryLevel = health.battery.level,
            charging = health.battery.charging,
            cpuClusters = clusters,
        )
    }

    private fun notificationsGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return getApplication<Application>().checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private suspend fun readAppsAnalysis(): StartupAnalysis = when {
        _state.value.snapshot.rootAvailable -> runCatching { startupRepository.analyzeCurrentBoot() }.getOrElse { StartupAnalysis() }
        _state.value.shizuku.ready -> frameworkAppCatalogRepository.read()
        else -> StartupAnalysis()
    }

    private fun appendPrivilegeDiagnostics(base: String): String {
        val bridge = _state.value.shizuku
        val probes = _state.value.shizukuProbes
        return buildString {
            append(base.trimEnd())
            append("\n\n[Privilege Bridge]\n")
            append("binder=").append(bridge.binderAlive)
            append(" permission=").append(bridge.permissionGranted)
            append(" backend=").append(bridge.backend.displayName)
            append(" uid=").append(bridge.uid ?: -1)
            append(" server=").append(bridge.serverVersion ?: -1)
            append(" patch=").append(bridge.serverPatchVersion ?: -1)
            append(" sui=").append(bridge.suiAvailable)
            append('\n')
            probes.forEach { probe ->
                append("probe ").append(probe.capability.name)
                    .append("=").append(if (probe.available) "PASS" else "FAIL")
                    .append(" backend=").append(probe.backend.displayName)
                    .append(" detail=").append(probe.detail.take(160))
                    .append('\n')
            }
        }
    }
}

