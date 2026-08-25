package com.arthur.roottools.automation

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.arthur.roottools.R
import com.arthur.roottools.app.rootToolsContainer
import com.arthur.roottools.core.shadow.ShadowDisplayPolicy
import com.arthur.roottools.core.shadow.ShadowDisplayTextStrategy
import com.arthur.roottools.data.DeviceHealthCollector
import com.arthur.roottools.feature.integrity.data.IntegrityBaselineStore
import com.arthur.roottools.feature.integrity.data.IntegrityReportStore
import com.arthur.roottools.feature.integrity.data.IntegrityRepository
import com.arthur.roottools.feature.integrity.model.IntegrityReportFormat
import com.arthur.roottools.feature.integrity.model.IntegrityScanMode
import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.model.PrivilegeRouteBackend
import com.arthur.roottools.model.ShadowDisplayActionResult
import com.arthur.roottools.model.ShadowDisplayStatus
import com.arthur.roottools.service.CpuPolicyService
import com.arthur.roottools.workflow.ManagedWorkflowController
import com.arthur.roottools.workflow.ManagedWorkflowId
import com.arthur.roottools.workflow.ManagedWorkflowRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Base64
import java.util.UUID

class ActionRouterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val component = intent.component ?: return
        if (component.packageName != context.packageName || component.className != javaClass.name) return
        val requestId = safeRequestId(intent.getStringExtra(EXTRA_REQUEST_ID))

        val command = AutomationCommand.parse(intent.getStringExtra(EXTRA_COMMAND)) ?: run {
            completeImmediate(
                requestId = requestId,
                command = "UNKNOWN",
                success = false,
                message = "Unsupported automation command",
            )
            return
        }
        val enabled = intent.takeIf {
            command == AutomationCommand.SET_ADB || command == AutomationCommand.SET_NATIVE_ADB
        }?.getBooleanExtra(EXTRA_ENABLED, true)
        val trustedAdbShadowRequest = AutomationTransportPolicy.isTrustedAdbShadowRequest(intent.flags, command)
        val token = intent.getStringExtra(EXTRA_TOKEN)
        val legacyAuthorized = ActionTokenStore(context).matches(token) &&
            AutomationAuthorizationPolicy.isAllowed(
                scopes = AutomationAuthorizationPolicy.termuxDefaultScopes,
                command = command,
                enabled = enabled,
            )
        val scopedClient = if (trustedAdbShadowRequest || legacyAuthorized) {
            null
        } else {
            AutomationClientStore(context).authorize(token, command, enabled)
        }
        if (!trustedAdbShadowRequest && !legacyAuthorized && scopedClient == null) {
            completeImmediate(
                requestId = requestId,
                command = command.wireName,
                success = false,
                message = "Automation credential or scope denied",
            )
            return
        }
        val rateLimitClientId = when {
            trustedAdbShadowRequest -> ADB_SHADOW_CLIENT_ID
            scopedClient != null -> scopedClient.clientId
            else -> null
        }
        if (rateLimitClientId != null && !AutomationRateLimiter.tryAcquire(rateLimitClientId)) {
            completeImmediate(requestId, command.wireName, false, "Automation client rate limit exceeded")
            return
        }

        val ordered = isOrderedBroadcast
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val result = runCatching { execute(context.applicationContext, intent, command, requestId) }
                .getOrElse { error ->
                    AutomationActionResult(
                        requestId = requestId,
                        command = command.wireName,
                        success = false,
                        message = error.message?.take(180) ?: "Automation action failed",
                    )
                }
            if (ordered) {
                pending.setResultCode(if (result.success) Activity.RESULT_OK else Activity.RESULT_CANCELED)
                pending.setResultData(result.toJson())
            }
            pending.finish()
        }
    }

    private suspend fun execute(
        context: Context,
        intent: Intent,
        command: AutomationCommand,
        requestId: String,
    ): AutomationActionResult {
        val container = context.rootToolsContainer
        val packageController = container.createPackagePolicyController("Automation")
        val shadowDisplayController = container.createShadowDisplayController("Automation")
        val agentSessionManager = container.agentSessionManager
        return when (command) {
            AutomationCommand.GET_STATUS -> {
                val adb = container.adbRepository.read()
                AutomationActionResult(
                    requestId = requestId,
                    command = command.wireName,
                    success = true,
                    message = "RootTools status ready",
                    payload = JSONObject()
                        .put("rootAvailable", container.shell.isAvailable(timeoutSeconds = 5))
                        .put("performanceMode", container.policyStore.mode.name)
                        .put("rootTcpAdb", adb.rootTcpEnabled)
                        .put("rootTcpPort", adb.rootTcpPort ?: JSONObject.NULL)
                        .put("nativeWirelessAdb", adb.nativeWirelessEnabled)
                        .put("nativeWirelessPort", adb.nativeTlsPort ?: JSONObject.NULL)
                        .put("tailscaleIpv4", adb.tailscaleIpv4 ?: JSONObject.NULL),
                )
            }
            AutomationCommand.SET_MODE -> {
                val mode = runCatching {
                    PerformanceMode.valueOf(intent.getStringExtra(EXTRA_MODE)?.uppercase().orEmpty())
                }.getOrNull() ?: return AutomationActionResult(
                    requestId,
                    command.wireName,
                    false,
                    "Invalid performance mode",
                )
                CpuPolicyService.setMode(context, mode, source = "Automation")
                AutomationActionResult(requestId, command.wireName, true, "Performance mode set to ${mode.name}")
            }
            AutomationCommand.SET_ADB -> {
                // Remote automation may ensure ADB is ON, but cannot turn off the current
                // management lifeline. Disabling is intentionally restricted to confirmed UI.
                if (!intent.getBooleanExtra(EXTRA_ENABLED, true)) {
                    AutomationActionResult(requestId, command.wireName, false, "Remote Root TCP ADB disable is not allowed")
                } else {
                    val action = container.createAdbController("Automation").setRootTcpEnabled(true)
                    AutomationActionResult(requestId, command.wireName, action.success, action.message, backend = "root")
                }
            }
            AutomationCommand.SET_NATIVE_ADB -> {
                val action = container.createAdbController("Automation").setNativeWirelessEnabled(
                    intent.getBooleanExtra(EXTRA_ENABLED, true),
                )
                AutomationActionResult(requestId, command.wireName, action.success, action.message, backend = "root")
            }
            AutomationCommand.FREEZE -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE)
                    ?: return AutomationActionResult(requestId, command.wireName, false, "Missing package")
                val action = packageController.freeze(packageName)
                AutomationActionResult(requestId, command.wireName, action.success, action.message)
            }
            AutomationCommand.UNFREEZE -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE)
                    ?: return AutomationActionResult(requestId, command.wireName, false, "Missing package")
                val action = packageController.enable(packageName)
                AutomationActionResult(requestId, command.wireName, action.success, action.message)
            }
            AutomationCommand.RUN_DIAGNOSTIC -> {
                val health = DeviceHealthCollector(container.shell).collect(includeProcesses = true)
                val repository = container.diagnosticsRepository
                val diagnostic = repository.collect()
                val file = container.reportStore.write(repository.buildSnapshotText(health, diagnostic))
                AutomationActionResult(
                    requestId = requestId,
                    command = command.wireName,
                    success = true,
                    message = "Diagnostic snapshot created",
                    payload = JSONObject().put("fileName", file.name),
                )
            }
            AutomationCommand.INTEGRITY_FAST_SCAN,
            AutomationCommand.INTEGRITY_DEEP_SCAN -> {
                val mode = if (command == AutomationCommand.INTEGRITY_DEEP_SCAN) IntegrityScanMode.DEEP else IntegrityScanMode.FAST
                val baseline = IntegrityBaselineStore(context).read()
                val snapshot = IntegrityRepository(context, container.shell).scan(mode, baseline)
                val report = IntegrityReportStore(context).write(snapshot, IntegrityReportFormat.JSON)
                AutomationActionResult(
                    requestId = requestId,
                    command = command.wireName,
                    success = true,
                    message = "Integrity scan completed",
                    payload = JSONObject()
                        .put("scanMode", mode.name)
                        .put("maxDisposition", snapshot.primaryDisposition.name)
                        .put("expectedCount", snapshot.expectedCount)
                        .put("warnCount", snapshot.warningCount)
                        .put("criticalCount", snapshot.criticalCount)
                        .put("baselineDriftCount", snapshot.baselineDriftCount)
                        .put("reportFileName", report.name),
                )
            }
            AutomationCommand.INTEGRITY_EXPORT_LAST_REPORT -> {
                val report = IntegrityReportStore(context).latestReport()
                    ?: return AutomationActionResult(requestId, command.wireName, false, "No integrity report is available")
                AutomationActionResult(
                    requestId = requestId,
                    command = command.wireName,
                    success = true,
                    message = "Integrity report ready",
                    payload = JSONObject().put("reportFileName", report.name),
                )
            }
            AutomationCommand.RUN_WORKFLOW -> {
                val workflowId = runCatching {
                    ManagedWorkflowId.valueOf(intent.getStringExtra(EXTRA_WORKFLOW)?.trim()?.uppercase().orEmpty())
                }.getOrNull() ?: return AutomationActionResult(
                    requestId,
                    command.wireName,
                    false,
                    "Unknown managed workflow",
                )
                val execution = ManagedWorkflowController(context).run(
                    ManagedWorkflowRequest(
                        workflowId = workflowId,
                        packageName = intent.getStringExtra(EXTRA_PACKAGE),
                    )
                )
                val steps = org.json.JSONArray().apply {
                    execution.steps.forEach { step ->
                        put(
                            JSONObject()
                                .put("type", step.type.name)
                                .put("success", step.success)
                                .put("message", step.message)
                                .apply {
                                    step.artifactName?.let { put("artifactName", it) }
                                    step.structuredOutput?.let { put("structuredOutput", it.take(12_000)) }
                                }
                        )
                    }
                }
                AutomationActionResult(
                    requestId = requestId,
                    command = command.wireName,
                    success = execution.success,
                    message = if (execution.success) "Managed workflow completed" else "Managed workflow stopped on failure",
                    payload = JSONObject()
                        .put("workflowId", execution.workflowId.name)
                        .put("startedAtEpochMs", execution.startedAtEpochMs)
                        .put("finishedAtEpochMs", execution.finishedAtEpochMs)
                        .put("steps", steps),
                )
            }
            AutomationCommand.SHADOW_STATUS -> {
                val status = shadowDisplayController.status().getOrElse { error ->
                    return AutomationActionResult(requestId, command.wireName, false, error.message ?: "Unable to read shadow display")
                }
                AutomationActionResult(
                    requestId = requestId,
                    command = command.wireName,
                    success = true,
                    message = "Shadow display status ready",
                    payload = status.toAutomationJson(),
                )
            }
            AutomationCommand.SHADOW_START -> {
                val action = shadowDisplayController.start(
                    width = intent.getIntExtra(EXTRA_WIDTH, DEFAULT_SHADOW_WIDTH),
                    height = intent.getIntExtra(EXTRA_HEIGHT, DEFAULT_SHADOW_HEIGHT),
                    densityDpi = intent.getIntExtra(EXTRA_DENSITY_DPI, DEFAULT_SHADOW_DENSITY_DPI),
                )
                val status = shadowDisplayController.status().getOrNull()
                if (action.success) {
                    agentSessionManager.ensureShadowSession()
                    agentSessionManager.updateStep(context.getString(R.string.agent_session_shadow_ready_step))
                }
                AutomationActionResult(
                    requestId = requestId,
                    command = command.wireName,
                    success = action.success,
                    message = if (action.success) "Shadow display started" else action.detail.ifBlank { "Shadow display start failed" },
                    backend = action.backend.name.lowercase(),
                    payload = status?.toAutomationJson(),
                )
            }
            AutomationCommand.SHADOW_STOP -> {
                val action = shadowDisplayController.stop()
                if (action.success) agentSessionManager.stop()
                AutomationActionResult(
                    requestId = requestId,
                    command = command.wireName,
                    success = action.success,
                    message = if (action.success) "Shadow display stopped" else action.detail.ifBlank { "Shadow display stop failed" },
                    backend = action.backend.name.lowercase(),
                )
            }
            AutomationCommand.SHADOW_LAUNCH -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE)
                    ?: return AutomationActionResult(requestId, command.wireName, false, "Missing package")
                val action = shadowDisplayController.launchPackage(packageName)
                if (action.success) {
                    val label = runCatching {
                        val info = context.packageManager.getApplicationInfo(packageName, 0)
                        context.packageManager.getApplicationLabel(info).toString()
                    }.getOrDefault(packageName)
                    agentSessionManager.updateStep(
                        context.getString(R.string.agent_session_running_app_step, label),
                        targetPackage = packageName,
                        targetLabel = label,
                    )
                }
                AutomationActionResult(
                    requestId,
                    command.wireName,
                    action.success,
                    if (action.success) "Package launched on shadow display" else action.detail.ifBlank { "Shadow launch failed" },
                    backend = action.backend.name.lowercase(),
                )
            }
            AutomationCommand.SHADOW_TAP -> {
                val action = shadowDisplayController.tap(
                    x = intent.getIntExtra(EXTRA_X, Int.MIN_VALUE),
                    y = intent.getIntExtra(EXTRA_Y, Int.MIN_VALUE),
                )
                if (action.success) agentSessionManager.updateStep(context.getString(R.string.agent_session_interacting_step))
                AutomationActionResult(
                    requestId,
                    command.wireName,
                    action.success,
                    if (action.success) "Shadow tap sent" else action.detail.ifBlank { "Shadow tap failed" },
                    backend = action.backend.name.lowercase(),
                )
            }
            AutomationCommand.SHADOW_SWIPE -> {
                val action = shadowDisplayController.swipe(
                    x1 = intent.getIntExtra(EXTRA_X1, Int.MIN_VALUE),
                    y1 = intent.getIntExtra(EXTRA_Y1, Int.MIN_VALUE),
                    x2 = intent.getIntExtra(EXTRA_X2, Int.MIN_VALUE),
                    y2 = intent.getIntExtra(EXTRA_Y2, Int.MIN_VALUE),
                    durationMs = intent.getIntExtra(EXTRA_DURATION_MS, DEFAULT_SHADOW_SWIPE_DURATION_MS),
                )
                if (action.success) agentSessionManager.updateStep(context.getString(R.string.agent_session_interacting_step))
                AutomationActionResult(
                    requestId,
                    command.wireName,
                    action.success,
                    if (action.success) "Shadow swipe sent" else action.detail.ifBlank { "Shadow swipe failed" },
                    backend = action.backend.name.lowercase(),
                )
            }
            AutomationCommand.SHADOW_TEXT -> {
                val text = intent.getStringExtra(EXTRA_TEXT)
                    ?: return AutomationActionResult(requestId, command.wireName, false, "Missing text")
                val action = sendShadowText(context, shadowDisplayController, text)
                if (action.success) agentSessionManager.updateStep(context.getString(R.string.agent_session_interacting_step))
                AutomationActionResult(
                    requestId,
                    command.wireName,
                    action.success,
                    if (action.success) "Shadow text sent" else action.detail.ifBlank { "Shadow text failed" },
                    backend = action.backend.name.lowercase(),
                )
            }
            AutomationCommand.SHADOW_CAPTURE -> {
                val preview = shadowDisplayController.capturePreview().getOrElse { error ->
                    return AutomationActionResult(requestId, command.wireName, false, error.message ?: "Shadow capture failed")
                }
                if (preview.size > MAX_AUTOMATION_PREVIEW_BYTES) {
                    return AutomationActionResult(requestId, command.wireName, false, "Shadow preview exceeds automation payload limit")
                }
                agentSessionManager.updateStep(context.getString(R.string.agent_session_preview_step))
                AutomationActionResult(
                    requestId = requestId,
                    command = command.wireName,
                    success = true,
                    message = "Shadow preview captured",
                    payload = JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("byteCount", preview.size)
                        .put("base64", Base64.getEncoder().encodeToString(preview)),
                )
            }
        }
    }

    private suspend fun sendShadowText(
        context: Context,
        controller: com.arthur.roottools.policy.ShadowDisplayController,
        text: String,
    ): ShadowDisplayActionResult = when (ShadowDisplayPolicy.textStrategy(text)) {
        ShadowDisplayTextStrategy.KEY_EVENTS -> controller.typeText(text)
        ShadowDisplayTextStrategy.CLIPBOARD_PASTE -> pasteShadowText(context, controller, text)
        null -> ShadowDisplayActionResult(
            success = false,
            backend = PrivilegeRouteBackend.NONE,
            detail = "Text is too long or contains unsupported control data",
        )
    }

    private suspend fun pasteShadowText(
        context: Context,
        controller: com.arthur.roottools.policy.ShadowDisplayController,
        text: String,
    ): ShadowDisplayActionResult {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
            ?: return ShadowDisplayActionResult(false, PrivilegeRouteBackend.NONE, "Clipboard service unavailable")
        val previousClip = runCatching { clipboard.primaryClip }.getOrNull()
        return try {
            clipboard.setPrimaryClip(ClipData.newPlainText("RootTools shadow input", text))
            val action = controller.paste()
            delay(SHADOW_CLIPBOARD_SETTLE_MS)
            action
        } catch (error: Throwable) {
            ShadowDisplayActionResult(
                success = false,
                backend = PrivilegeRouteBackend.NONE,
                detail = error.message?.take(160) ?: "Unable to paste Unicode text",
            )
        } finally {
            runCatching {
                if (previousClip != null) clipboard.setPrimaryClip(previousClip)
                else clipboard.clearPrimaryClip()
            }
        }
    }

    private fun ShadowDisplayStatus.toAutomationJson(): JSONObject = JSONObject()
        .put("state", state.name)
        .put("running", running)
        .put("displayId", displayId ?: JSONObject.NULL)
        .put("pid", pid ?: JSONObject.NULL)
        .put("width", config.width)
        .put("height", config.height)
        .put("densityDpi", config.densityDpi)
        .put("processAlive", processAlive)
        .put("displayActive", displayActive)
        .put("startedAtMs", startedAtMs ?: JSONObject.NULL)
        .put("error", error ?: JSONObject.NULL)

    private fun completeImmediate(
        requestId: String,
        command: String,
        success: Boolean,
        message: String,
    ) {
        if (!isOrderedBroadcast) return
        setResultCode(if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED)
        setResultData(AutomationActionResult(requestId, command, success, message).toJson())
    }

    private fun safeRequestId(candidate: String?): String {
        val trimmed = candidate?.trim().orEmpty()
        return if (REQUEST_ID_REGEX.matches(trimmed)) trimmed else UUID.randomUUID().toString()
    }

    companion object {
        const val ACTION = "com.arthur.roottools.ACTION"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_MODE = "mode"
        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_WORKFLOW = "workflow"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_DENSITY_DPI = "density_dpi"
        const val EXTRA_X = "x"
        const val EXTRA_Y = "y"
        const val EXTRA_X1 = "x1"
        const val EXTRA_Y1 = "y1"
        const val EXTRA_X2 = "x2"
        const val EXTRA_Y2 = "y2"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_TEXT = "text"
        const val EXTRA_REQUEST_ID = "request_id"
        private const val DEFAULT_SHADOW_WIDTH = 720
        private const val DEFAULT_SHADOW_HEIGHT = 1600
        private const val DEFAULT_SHADOW_DENSITY_DPI = 320
        private const val DEFAULT_SHADOW_SWIPE_DURATION_MS = 300
        private const val SHADOW_CLIPBOARD_SETTLE_MS = 300L
        private const val MAX_AUTOMATION_PREVIEW_BYTES = 512 * 1024
        private const val ADB_SHADOW_CLIENT_ID = "adb-shell-shadow"
        private val REQUEST_ID_REGEX = Regex("^[A-Za-z0-9._:-]{1,80}$")
    }
}
