package com.arthur.roottools.workflow

import android.content.Context
import com.arthur.roottools.data.DeviceHealthCollector
import com.arthur.roottools.data.DeviceRepository
import com.arthur.roottools.data.DiagnosticReportStore
import com.arthur.roottools.data.DiagnosticsRepository
import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.integration.termux.TermuxManagedTaskId
import com.arthur.roottools.integration.termux.TermuxTaskController
import com.arthur.roottools.policy.PackagePolicyController
import com.arthur.roottools.root.RootShell
import com.arthur.roottools.service.CpuPolicyService

/** Cross-feature orchestration that reuses existing semantic controllers and repositories. */
class ManagedWorkflowController(context: Context) {
    private val appContext = context.applicationContext
    private val shell = RootShell()
    private val rootAudit = RootActionAuditStore(appContext)
    private val deviceRepository = DeviceRepository(shell, rootAudit, "Workflow")
    private val packageController = PackagePolicyController(shell, rootAudit, "Workflow")
    private val diagnosticsRepository = DiagnosticsRepository(shell)
    private val reportStore = DiagnosticReportStore(appContext)
    private val termuxController = TermuxTaskController(appContext)
    private val auditStore = ManagedWorkflowAuditStore(appContext)

    suspend fun run(request: ManagedWorkflowRequest): ManagedWorkflowExecutionResult {
        val started = System.currentTimeMillis()
        val validation = ManagedWorkflowPolicy.validate(request)
        if (!validation.valid) {
            return ManagedWorkflowExecutionResult(
                workflowId = request.workflowId,
                success = false,
                startedAtEpochMs = started,
                finishedAtEpochMs = System.currentTimeMillis(),
                steps = listOf(
                    ManagedWorkflowStepResult(
                        type = ManagedWorkflowCatalog.get(request.workflowId).steps.first().type,
                        success = false,
                        message = validation.message,
                    )
                ),
            )
        }

        val definition = ManagedWorkflowCatalog.get(request.workflowId)
        val results = mutableListOf<ManagedWorkflowStepResult>()
        var diagnosticText: String? = null

        for (step in definition.steps) {
            val result = when (step.type) {
                ManagedWorkflowStepType.SET_PERFORMANCE_MODE -> {
                    val mode = requireNotNull(step.performanceMode)
                    runCatching {
                        CpuPolicyService.setMode(appContext, mode, source = "Workflow:${definition.id.name}")
                    }.fold(
                        onSuccess = { ManagedWorkflowStepResult(step.type, true, "Performance mode requested: ${mode.name}") },
                        onFailure = { ManagedWorkflowStepResult(step.type, false, it.message ?: "Unable to request performance mode") },
                    )
                }

                ManagedWorkflowStepType.ENSURE_ROOT_ADB -> {
                    val success = deviceRepository.setAdbTcpEnabled(true)
                    ManagedWorkflowStepResult(
                        step.type,
                        success,
                        if (success) "Root TCP ADB enabled" else "Root TCP ADB enable failed",
                    )
                }

                ManagedWorkflowStepType.ENABLE_TARGET_APP -> {
                    val packageName = requireNotNull(request.packageName)
                    val action = packageController.enable(packageName)
                    ManagedWorkflowStepResult(step.type, action.success, action.message)
                }

                ManagedWorkflowStepType.RUN_DIAGNOSTIC -> {
                    val health = DeviceHealthCollector(shell).collect(includeProcesses = true)
                    val diagnostic = diagnosticsRepository.collect()
                    val text = diagnosticsRepository.buildSnapshotText(health, diagnostic)
                    diagnosticText = text
                    val file = reportStore.write(text)
                    ManagedWorkflowStepResult(
                        step.type,
                        success = true,
                        message = "Diagnostic snapshot created",
                        artifactName = file.name,
                    )
                }

                ManagedWorkflowStepType.TERMUX_RUNTIME_PROBE -> {
                    val task = termuxController.run(TermuxManagedTaskId.RUNTIME_PROBE)
                    ManagedWorkflowStepResult(
                        step.type,
                        task.success,
                        task.transportError ?: task.internalErrorMessage.ifBlank {
                            if (task.success) "Termux runtime probe completed" else "Termux runtime probe failed"
                        },
                        structuredOutput = task.stdout.takeIf { task.success },
                    )
                }

                ManagedWorkflowStepType.TERMUX_POST_PROCESS_DIAGNOSTIC -> {
                    val source = diagnosticText
                    if (source == null) {
                        ManagedWorkflowStepResult(step.type, false, "No diagnostic snapshot is available")
                    } else {
                        val task = termuxController.postProcessDiagnostic(source)
                        ManagedWorkflowStepResult(
                            step.type,
                            task.success,
                            task.transportError ?: task.internalErrorMessage.ifBlank {
                                if (task.success) "Diagnostic post-processing completed" else "Diagnostic post-processing failed"
                            },
                            structuredOutput = task.stdout.takeIf { task.success },
                        )
                    }
                }
            }
            results += result
            if (!result.success) break
        }

        val finished = System.currentTimeMillis()
        val execution = ManagedWorkflowExecutionResult(
            workflowId = request.workflowId,
            success = results.size == definition.steps.size && results.all { it.success },
            startedAtEpochMs = started,
            finishedAtEpochMs = finished,
            steps = results,
        )
        auditStore.record(
            ManagedWorkflowAuditRecord(
                timestampMs = started,
                workflowId = definition.id,
                success = execution.success,
                completedSteps = results.count { it.success },
                durationMs = (finished - started).coerceAtLeast(0L),
            )
        )
        return execution
    }

    fun audit(limit: Int = 20): List<ManagedWorkflowAuditRecord> = auditStore.read(limit)
}

