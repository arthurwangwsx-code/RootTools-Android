package com.arthur.roottools.automation

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arthur.roottools.app.rootToolsContainer
import com.arthur.roottools.data.DeviceHealthCollector
import com.arthur.roottools.feature.integrity.data.IntegrityBaselineStore
import com.arthur.roottools.feature.integrity.data.IntegrityReportStore
import com.arthur.roottools.feature.integrity.data.IntegrityRepository
import com.arthur.roottools.feature.integrity.model.IntegrityReportFormat
import com.arthur.roottools.feature.integrity.model.IntegrityScanMode
import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.service.CpuPolicyService
import com.arthur.roottools.workflow.ManagedWorkflowController
import com.arthur.roottools.workflow.ManagedWorkflowId
import com.arthur.roottools.workflow.ManagedWorkflowRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
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
        val token = intent.getStringExtra(EXTRA_TOKEN)
        val legacyAuthorized = ActionTokenStore(context).matches(token) &&
            AutomationAuthorizationPolicy.isAllowed(
                scopes = AutomationAuthorizationPolicy.termuxDefaultScopes,
                command = command,
                enabled = enabled,
            )
        val scopedClient = if (legacyAuthorized) {
            null
        } else {
            AutomationClientStore(context).authorize(token, command, enabled)
        }
        if (!legacyAuthorized && scopedClient == null) {
            completeImmediate(
                requestId = requestId,
                command = command.wireName,
                success = false,
                message = "Automation credential or scope denied",
            )
            return
        }
        if (scopedClient != null && !AutomationRateLimiter.tryAcquire(scopedClient.clientId)) {
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
        }
    }

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
        const val EXTRA_REQUEST_ID = "request_id"
        private val REQUEST_ID_REGEX = Regex("^[A-Za-z0-9._:-]{1,80}$")
    }
}
