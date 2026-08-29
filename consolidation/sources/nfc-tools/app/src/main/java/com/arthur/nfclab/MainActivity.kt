package com.arthur.nfclab

import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.arthur.nfclab.domain.AccessReaderOutcome
import com.arthur.nfclab.domain.NfcDeviceProfile
import com.arthur.nfclab.domain.NfcOperatingMode
import com.arthur.nfclab.domain.ProvisioningCapabilityReport
import com.arthur.nfclab.hce.LabHostApduService
import com.arthur.nfclab.hce.LabApduProtocol
import com.arthur.nfclab.hce.HceCompatibilityTrace
import com.arthur.nfclab.hce.HceCompatibilityTraceStore
import com.arthur.nfclab.nfc.NfcTagInspector
import com.arthur.nfclab.nfc.TagSnapshot
import com.arthur.nfclab.platform.DeviceNfcProfileRepository
import com.arthur.nfclab.platform.access.AccessDiagnosticsManager
import com.arthur.nfclab.platform.simulation.SimulationCapabilityAnalyzer
import com.arthur.nfclab.platform.provisioning.ProvisioningCapabilityRepository
import com.arthur.nfclab.platform.runtime.AndroidNfcModeDriver
import com.arthur.nfclab.platform.runtime.NfcModeController
import com.arthur.nfclab.platform.runtime.NfcSystemStateObserver
import com.arthur.nfclab.root.RootNfcDiagnostics
import com.arthur.nfclab.storage.ScanHistoryStore
import com.arthur.nfclab.storage.ScanAutomationStore
import com.arthur.nfclab.storage.DeviceProfileStore
import com.arthur.nfclab.storage.AccessDiagnosticStore
import com.arthur.nfclab.ui.AccessDiagnosticPhase
import com.arthur.nfclab.ui.AccessDiagnosticUiState
import com.arthur.nfclab.ui.NfcLabApp
import com.arthur.nfclab.ui.NfcToolsActions
import com.arthur.nfclab.ui.NfcToolsUiState
import java.util.concurrent.Executors

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {
    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private lateinit var historyStore: ScanHistoryStore
    private lateinit var automationStore: ScanAutomationStore
    private lateinit var deviceProfileRepository: DeviceNfcProfileRepository
    private lateinit var deviceProfileStore: DeviceProfileStore
    private lateinit var provisioningCapabilityRepository: ProvisioningCapabilityRepository
    private lateinit var accessDiagnosticsManager: AccessDiagnosticsManager
    private lateinit var accessDiagnosticStore: AccessDiagnosticStore
    private lateinit var hceTraceStore: HceCompatibilityTraceStore
    private lateinit var modeController: NfcModeController
    private lateinit var nfcSystemStateObserver: NfcSystemStateObserver
    private val mainHandler = Handler(Looper.getMainLooper())
    private var automationSessionId: String? = null
    private var automationStartedAtMs: Long = 0L
    private var accessAutomationSessionId: String? = null
    private var walletRefreshPending = false

    private var labMode by mutableStateOf(NfcOperatingMode.DEFAULT)
    private var nfcEnabled by mutableStateOf(false)
    private var lastSnapshot by mutableStateOf<TagSnapshot?>(null)
    private var history by mutableStateOf<List<TagSnapshot>>(emptyList())
    private var rootReport by mutableStateOf("尚未运行 Root NFC 诊断")
    private var rootRunning by mutableStateOf(false)
    private var hcePayload by mutableStateOf("NFC Tools / authorized test credential")
    private var hceTrace by mutableStateOf(HceCompatibilityTrace())
    private var deviceProfile by mutableStateOf<NfcDeviceProfile?>(null)
    private var deviceProfileLoading by mutableStateOf(false)
    private var provisioningCapability by mutableStateOf<ProvisioningCapabilityReport?>(null)
    private var accessDiagnostic by mutableStateOf(AccessDiagnosticUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        historyStore = ScanHistoryStore(this)
        automationStore = ScanAutomationStore(this)
        deviceProfileRepository = DeviceNfcProfileRepository(this)
        deviceProfileStore = DeviceProfileStore(this)
        provisioningCapabilityRepository = ProvisioningCapabilityRepository()
        accessDiagnosticsManager = AccessDiagnosticsManager(this)
        accessDiagnosticStore = AccessDiagnosticStore(this)
        hceTraceStore = HceCompatibilityTraceStore(this)
        modeController = NfcModeController(
            driver = AndroidNfcModeDriver(this, nfcAdapter, this),
            onApplied = { mode, readerEnabled ->
                Log.i(SCAN_TAG, "mode=${mode.name} readerMode=$readerEnabled")
            },
        )
        nfcEnabled = nfcAdapter?.isEnabled == true
        nfcSystemStateObserver = NfcSystemStateObserver(this, nfcAdapter) { enabled ->
            nfcEnabled = enabled
            if (modeController.isResumed) modeController.applyDesiredMode()
        }
        history = historyStore.load()
        deviceProfile = deviceProfileStore.load()
        provisioningCapability = deviceProfile?.let { profile -> provisioningCapabilityRepository.collect(profile) }
        accessDiagnostic = AccessDiagnosticUiState(
            supported = accessDiagnosticsManager.supportedProvider(deviceProfile) != null,
            providerId = accessDiagnosticsManager.supportedProvider(deviceProfile)?.id,
            history = accessDiagnosticStore.load(),
        )
        hcePayload = getSharedPreferences(LabHostApduService.PREFS, MODE_PRIVATE)
            .getString(LabHostApduService.KEY_PAYLOAD, hcePayload)
            ?: hcePayload
        hceTrace = hceTraceStore.load()

        setContent {
            NfcLabApp(
                state = NfcToolsUiState(
                    operatingMode = labMode,
                    nfcAvailable = nfcAdapter != null,
                    nfcEnabled = nfcEnabled,
                    supportsHce = packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION),
                    supportsHceF = packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION_NFCF),
                    lastSnapshot = lastSnapshot,
                    history = history,
                    hcePayload = hcePayload,
                    hceTrace = hceTrace,
                    rootReport = rootReport,
                    rootRunning = rootRunning,
                    deviceProfile = deviceProfile,
                    deviceProfileLoading = deviceProfileLoading,
                    provisioningCapability = provisioningCapability,
                    accessDiagnostic = accessDiagnostic,
                ),
                actions = NfcToolsActions(
                    onModeChange = ::switchMode,
                    onClearHistory = {
                        historyStore.clear()
                        history = emptyList()
                        lastSnapshot = null
                    },
                    onHcePayloadChange = ::updateHcePayload,
                    onRunRootDiagnostics = ::runRootDiagnostics,
                    onRefreshDeviceProfile = { refreshDeviceProfile() },
                    onOpenWallet = ::openWallet,
                    onStartAccessDiagnostic = ::startAccessDiagnostic,
                    onFinishAccessDiagnostic = ::finishAccessDiagnostic,
                    onClearAccessDiagnosticHistory = ::clearAccessDiagnosticHistory,
                ),
            )
        }

        handleAutomationIntent(intent)
        if (intent?.getStringExtra(EXTRA_AUTOMATION_MODE).isNullOrBlank()) {
            mainHandler.postDelayed({ refreshDeviceProfile() }, DEFAULT_PROFILE_REFRESH_DELAY_MS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutomationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        modeController.onResume()
        if (labMode == NfcOperatingMode.HCE) startHceTraceRefresh()
        if (walletRefreshPending) {
            walletRefreshPending = false
            mainHandler.postDelayed({ refreshDeviceProfile() }, WALLET_RETURN_REFRESH_DELAY_MS)
        }
    }

    override fun onStart() {
        super.onStart()
        nfcSystemStateObserver.start()
    }

    override fun onPause() {
        stopHceTraceRefresh(loadFinalState = true)
        modeController.onPause()
        super.onPause()
    }

    override fun onStop() {
        nfcSystemStateObserver.stop()
        super.onStop()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        accessDiagnosticsManager.cancel()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun switchMode(mode: NfcOperatingMode) {
        if (mode == NfcOperatingMode.HCE && labMode != NfcOperatingMode.HCE) {
            hceTraceStore.clear()
            hceTrace = HceCompatibilityTrace()
        }
        labMode = mode
        modeController.setMode(mode)
        if (mode == NfcOperatingMode.HCE && modeController.isResumed) {
            startHceTraceRefresh()
        } else {
            stopHceTraceRefresh(loadFinalState = true)
        }
    }

    private val hceTraceRefresh = object : Runnable {
        override fun run() {
            if (labMode != NfcOperatingMode.HCE || !modeController.isResumed) return
            hceTrace = hceTraceStore.load()
            mainHandler.postDelayed(this, HCE_TRACE_REFRESH_MS)
        }
    }

    private fun startHceTraceRefresh() {
        mainHandler.removeCallbacks(hceTraceRefresh)
        hceTrace = hceTraceStore.load()
        mainHandler.postDelayed(hceTraceRefresh, HCE_TRACE_REFRESH_MS)
    }

    private fun stopHceTraceRefresh(loadFinalState: Boolean) {
        mainHandler.removeCallbacks(hceTraceRefresh)
        if (loadFinalState) hceTrace = hceTraceStore.load()
    }

    override fun onTagDiscovered(tag: Tag) {
        val snapshot = NfcTagInspector.inspect(tag)
        Log.i(
            SCAN_TAG,
            "tagDiscovered id=${snapshot.idHex} tech=${snapshot.technologies.joinToString(",")}",
        )
        historyStore.save(snapshot)
        automationStore.writeDetected(
            sessionId = automationSessionId,
            startedAtMs = automationStartedAtMs,
            snapshot = snapshot,
        )
        runOnUiThread {
            lastSnapshot = snapshot
            history = historyStore.load()
        }
    }

    private fun handleAutomationIntent(intent: Intent?) {
        val mode = intent?.getStringExtra(EXTRA_AUTOMATION_MODE) ?: return

        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
            ?.takeIf { it.isNotBlank() }
            ?: "adb-${System.currentTimeMillis()}"
        if (mode.equals("hce", ignoreCase = true)) {
            automationSessionId = sessionId
            automationStartedAtMs = System.currentTimeMillis()
            switchMode(NfcOperatingMode.HCE)
            automationStore.writeHceReady(
                sessionId = sessionId,
                startedAtMs = automationStartedAtMs,
                aid = LabApduProtocol.AID,
                payload = hcePayload,
                nfcAvailable = nfcAdapter != null,
                nfcEnabled = nfcAdapter?.isEnabled == true,
                supportsHce = packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION),
            )
            Log.i(SCAN_TAG, "automationHce ready session=$sessionId aid=${LabApduProtocol.AID}")
            return
        }
        if (mode.equals("root_diag", ignoreCase = true)) {
            switchMode(NfcOperatingMode.DEFAULT)
            startRootDiagnostics(sessionId)
            return
        }
        if (mode.equals("device_profile", ignoreCase = true) ||
            mode.equals("xiaomi_profile", ignoreCase = true)
        ) {
            refreshDeviceProfile(sessionId, writeLegacyXiaomiFile = mode.equals("xiaomi_profile", ignoreCase = true))
            return
        }
        if (mode.equals("simulation_capability", ignoreCase = true)) {
            writeSimulationCapability(sessionId)
            return
        }
        if (mode.equals("provisioning_capability", ignoreCase = true)) {
            writeProvisioningCapability(sessionId)
            return
        }
        if (mode.equals("access_diag_start", ignoreCase = true)) {
            accessAutomationSessionId = sessionId
            startAccessDiagnostic()
            return
        }
        if (mode.equals("access_diag_stop", ignoreCase = true)) {
            accessAutomationSessionId = sessionId
            val outcome = when (intent.getStringExtra(EXTRA_ACCESS_OUTCOME)?.lowercase()) {
                "opened", "success" -> AccessReaderOutcome.OPENED
                "reacted", "reacted_failed" -> AccessReaderOutcome.REACTED_BUT_FAILED
                else -> AccessReaderOutcome.NO_REACTION
            }
            finishAccessDiagnostic(outcome)
            return
        }
        if (!mode.equals("reader", ignoreCase = true)) return
        val timeoutMs = intent.getLongExtra(EXTRA_TIMEOUT_MS, DEFAULT_SCAN_TIMEOUT_MS)
            .coerceIn(1_000L, 30_000L)
        val shouldRearmReader = intent.getBooleanExtra(EXTRA_REARM_READER, false)

        automationSessionId = sessionId
        automationStartedAtMs = System.currentTimeMillis()
        switchMode(NfcOperatingMode.READER)
        automationStore.writeWaiting(
            sessionId = sessionId,
            startedAtMs = automationStartedAtMs,
            timeoutMs = timeoutMs,
            nfcAvailable = nfcAdapter != null,
            nfcEnabled = nfcAdapter?.isEnabled == true,
        )
        Log.i(SCAN_TAG, "automationScan start session=$sessionId timeoutMs=$timeoutMs")

        if (shouldRearmReader) {
            mainHandler.postDelayed({
                if (automationSessionId != sessionId || !modeController.isResumed) return@postDelayed
                mainHandler.postDelayed({
                    if (automationSessionId == sessionId && modeController.rearmReaderMode()) {
                        Log.i(SCAN_TAG, "automationScan rearmed session=$sessionId")
                    }
                }, READER_REARM_GAP_MS)
            }, READER_REARM_DELAY_MS)
        }

        mainHandler.postDelayed({
            if (automationSessionId != sessionId) return@postDelayed
            if (automationStore.isWaiting(sessionId)) {
                automationStore.writeTimeout(
                    sessionId = sessionId,
                    startedAtMs = automationStartedAtMs,
                    timeoutMs = timeoutMs,
                )
                Log.w(SCAN_TAG, "automationScan timeout session=$sessionId noTagActivation=true")
            }
        }, timeoutMs)
    }

    private fun updateHcePayload(value: String) {
        hcePayload = value.take(160)
        getSharedPreferences(LabHostApduService.PREFS, MODE_PRIVATE).edit {
            putString(LabHostApduService.KEY_PAYLOAD, hcePayload)
        }
    }

    private fun writeSimulationCapability(sessionId: String) {
        val latestSnapshot = lastSnapshot ?: history.firstOrNull()
        val currentProfile = deviceProfile
        ioExecutor.execute {
            val profile = currentProfile ?: deviceProfileRepository.collect()
            val report = SimulationCapabilityAnalyzer.analyze(
                snapshot = latestSnapshot,
                profile = profile,
                supportsHostHce = packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION),
                provisioning = provisioningCapabilityRepository.collect(profile),
            )
            automationStore.writeSimulationCapability(sessionId, report)
            if (currentProfile == null) {
                runOnUiThread { deviceProfile = profile }
            }
        }
    }

    private fun writeProvisioningCapability(sessionId: String) {
        val currentProfile = deviceProfile
        ioExecutor.execute {
            val profile = currentProfile ?: deviceProfileRepository.collect()
            val report = provisioningCapabilityRepository.collect(profile)
            automationStore.writeProvisioningCapability(sessionId, report)
            runOnUiThread {
                if (currentProfile == null) deviceProfile = profile
                provisioningCapability = report
            }
        }
    }

    private fun runRootDiagnostics() {
        startRootDiagnostics(null)
    }

    private fun refreshDeviceProfile(
        sessionId: String? = null,
        writeLegacyXiaomiFile: Boolean = false,
    ) {
        if (deviceProfileLoading && sessionId == null) return
        deviceProfileLoading = true
        ioExecutor.execute {
            val profile = deviceProfileRepository.collect()
            val provisioning = provisioningCapabilityRepository.collect(profile)
            if (profile.error.isNullOrBlank()) deviceProfileStore.save(profile)
            sessionId?.let {
                automationStore.writeDeviceProfile(it, profile)
                if (writeLegacyXiaomiFile) automationStore.writeLegacyXiaomiProfile(it, profile)
            }
            runOnUiThread {
                deviceProfile = profile
                provisioningCapability = provisioning
                deviceProfileLoading = false
                val provider = accessDiagnosticsManager.supportedProvider(profile)
                accessDiagnostic = accessDiagnostic.copy(
                    supported = provider != null,
                    providerId = provider?.id,
                )
                if (modeController.isResumed) modeController.applyDesiredMode()
            }
        }
    }

    private fun startAccessDiagnostic() {
        if (accessDiagnostic.phase == AccessDiagnosticPhase.RUNNING ||
            accessDiagnostic.phase == AccessDiagnosticPhase.ANALYZING ||
            accessDiagnostic.phase == AccessDiagnosticPhase.STARTING
        ) return

        val profile = deviceProfile
        if (profile == null) {
            accessDiagnostic = accessDiagnostic.copy(
                phase = AccessDiagnosticPhase.ERROR,
                error = "设备能力尚未读取完成，请先刷新设备状态。",
            )
            return
        }
        val activeCard = profile.activeCard
        if (activeCard == null) {
            accessDiagnostic = accessDiagnostic.copy(
                phase = AccessDiagnosticPhase.ERROR,
                error = "没有检测到当前激活门卡。请先在系统钱包中选择要测试的门卡，再返回 NFC Tools。",
            )
            return
        }

        switchMode(NfcOperatingMode.DEFAULT)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        accessDiagnostic = accessDiagnostic.copy(
            phase = AccessDiagnosticPhase.STARTING,
            currentCardTitle = activeCard.title,
            report = null,
            error = null,
        )
        ioExecutor.execute {
            val result = accessDiagnosticsManager.start(profile, activeCard)
            runOnUiThread {
                if (result.isSuccess) {
                    accessAutomationSessionId?.let { sessionId ->
                        automationStore.writeAccessDiagnosticRunning(sessionId, activeCard.title)
                    }
                    accessDiagnostic = accessDiagnostic.copy(
                        phase = AccessDiagnosticPhase.RUNNING,
                        error = null,
                    )
                } else {
                    accessAutomationSessionId?.let { sessionId ->
                        automationStore.writeAccessDiagnosticError(
                            sessionId,
                            result.exceptionOrNull()?.message ?: "diagnostic start failed",
                        )
                    }
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    accessDiagnostic = accessDiagnostic.copy(
                        phase = AccessDiagnosticPhase.ERROR,
                        error = result.exceptionOrNull()?.message ?: "门禁诊断启动失败",
                    )
                }
            }
        }
    }

    private fun finishAccessDiagnostic(outcome: AccessReaderOutcome) {
        if (accessDiagnostic.phase != AccessDiagnosticPhase.RUNNING) return
        accessDiagnostic = accessDiagnostic.copy(phase = AccessDiagnosticPhase.ANALYZING, error = null)
        ioExecutor.execute {
            val result = accessDiagnosticsManager.stop(outcome)
            result.getOrNull()?.let(accessDiagnosticStore::save)
            val updatedHistory = accessDiagnosticStore.load()
            val automationId = accessAutomationSessionId
            if (automationId != null) {
                result.getOrNull()?.let { automationStore.writeAccessDiagnosticReport(automationId, it) }
                    ?: automationStore.writeAccessDiagnosticError(
                        automationId,
                        result.exceptionOrNull()?.message ?: "diagnostic analysis failed",
                    )
                accessAutomationSessionId = null
            }
            runOnUiThread {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                accessDiagnostic = if (result.isSuccess) {
                    accessDiagnostic.copy(
                        phase = AccessDiagnosticPhase.COMPLETE,
                        report = result.getOrNull(),
                        history = updatedHistory,
                        error = null,
                    )
                } else {
                    accessDiagnostic.copy(
                        phase = AccessDiagnosticPhase.ERROR,
                        history = updatedHistory,
                        error = result.exceptionOrNull()?.message ?: "门禁诊断分析失败",
                    )
                }
            }
        }
    }

    private fun clearAccessDiagnosticHistory() {
        if (accessDiagnostic.phase == AccessDiagnosticPhase.RUNNING ||
            accessDiagnostic.phase == AccessDiagnosticPhase.ANALYZING
        ) return
        accessDiagnosticStore.clear()
        accessDiagnostic = accessDiagnostic.copy(history = emptyList(), report = null)
    }

    private fun openWallet(providerId: String) {
        val wallet = deviceProfile?.wallets?.firstOrNull { it.providerId == providerId } ?: return
        val managementIntent = wallet.managementAction?.let { action ->
            Intent(action).setPackage(wallet.packageName).takeIf { intent ->
                intent.resolveActivity(packageManager) != null
            }
        }
        val launchIntent = managementIntent
            ?: packageManager.getLaunchIntentForPackage(wallet.packageName)
            ?: return
        walletRefreshPending = true
        runCatching { startActivity(launchIntent) }
            .onFailure { walletRefreshPending = false }
    }

    private fun startRootDiagnostics(sessionId: String?) {
        if (rootRunning) return
        rootRunning = true
        rootReport = "正在通过 su 读取 NFC 系统状态…"
        ioExecutor.execute {
            val report = RootNfcDiagnostics.collect(applicationContext)
            sessionId?.let { automationStore.writeRootDiagnostics(it, report) }
            runOnUiThread {
                rootReport = report
                rootRunning = false
                refreshDeviceProfile()
            }
        }
    }

    companion object {
        private const val SCAN_TAG = "NfcLabScan"
        private const val EXTRA_AUTOMATION_MODE = "automation_mode"
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_TIMEOUT_MS = "timeout_ms"
        private const val EXTRA_REARM_READER = "rearm_reader"
        private const val EXTRA_ACCESS_OUTCOME = "access_outcome"
        private const val DEFAULT_SCAN_TIMEOUT_MS = 8_000L
        private const val DEFAULT_PROFILE_REFRESH_DELAY_MS = 600L
        private const val WALLET_RETURN_REFRESH_DELAY_MS = 350L
        private const val HCE_TRACE_REFRESH_MS = 500L
        private const val READER_REARM_DELAY_MS = 900L
        private const val READER_REARM_GAP_MS = 250L
    }
}

