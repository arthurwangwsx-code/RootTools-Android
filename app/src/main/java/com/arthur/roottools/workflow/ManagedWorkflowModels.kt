package com.arthur.roottools.workflow

import com.arthur.roottools.model.PerformanceMode

enum class ManagedWorkflowId {
    TEST_DEVICE_READY,
    APP_TEST_READY,
    DIAGNOSTIC_PIPELINE,
    DEVELOPER_RUNTIME_HEALTH,
}

enum class ManagedWorkflowStepType {
    SET_PERFORMANCE_MODE,
    ENSURE_ROOT_ADB,
    ENABLE_TARGET_APP,
    RUN_DIAGNOSTIC,
    TERMUX_RUNTIME_PROBE,
    TERMUX_POST_PROCESS_DIAGNOSTIC,
}

data class ManagedWorkflowStep(
    val type: ManagedWorkflowStepType,
    val performanceMode: PerformanceMode? = null,
)

data class ManagedWorkflowDefinition(
    val id: ManagedWorkflowId,
    val version: Int,
    val title: String,
    val requiresPackageName: Boolean = false,
    val steps: List<ManagedWorkflowStep>,
)

data class ManagedWorkflowRequest(
    val workflowId: ManagedWorkflowId,
    val packageName: String? = null,
)

data class ManagedWorkflowStepResult(
    val type: ManagedWorkflowStepType,
    val success: Boolean,
    val message: String,
    val artifactName: String? = null,
    val structuredOutput: String? = null,
)

data class ManagedWorkflowExecutionResult(
    val workflowId: ManagedWorkflowId,
    val success: Boolean,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long,
    val steps: List<ManagedWorkflowStepResult>,
)

object ManagedWorkflowCatalog {
    private val definitions = listOf(
        ManagedWorkflowDefinition(
            id = ManagedWorkflowId.TEST_DEVICE_READY,
            version = 1,
            title = "Test device ready",
            steps = listOf(
                ManagedWorkflowStep(ManagedWorkflowStepType.SET_PERFORMANCE_MODE, PerformanceMode.AUTO),
                ManagedWorkflowStep(ManagedWorkflowStepType.ENSURE_ROOT_ADB),
                ManagedWorkflowStep(ManagedWorkflowStepType.RUN_DIAGNOSTIC),
            ),
        ),
        ManagedWorkflowDefinition(
            id = ManagedWorkflowId.APP_TEST_READY,
            version = 1,
            title = "App test ready",
            requiresPackageName = true,
            steps = listOf(
                ManagedWorkflowStep(ManagedWorkflowStepType.ENABLE_TARGET_APP),
                ManagedWorkflowStep(ManagedWorkflowStepType.SET_PERFORMANCE_MODE, PerformanceMode.AUTO),
                ManagedWorkflowStep(ManagedWorkflowStepType.ENSURE_ROOT_ADB),
            ),
        ),
        ManagedWorkflowDefinition(
            id = ManagedWorkflowId.DIAGNOSTIC_PIPELINE,
            version = 1,
            title = "Diagnostic pipeline",
            steps = listOf(
                ManagedWorkflowStep(ManagedWorkflowStepType.RUN_DIAGNOSTIC),
                ManagedWorkflowStep(ManagedWorkflowStepType.TERMUX_POST_PROCESS_DIAGNOSTIC),
            ),
        ),
        ManagedWorkflowDefinition(
            id = ManagedWorkflowId.DEVELOPER_RUNTIME_HEALTH,
            version = 1,
            title = "Developer runtime health",
            steps = listOf(
                ManagedWorkflowStep(ManagedWorkflowStepType.TERMUX_RUNTIME_PROBE),
            ),
        ),
    ).associateBy { it.id }

    fun get(id: ManagedWorkflowId): ManagedWorkflowDefinition = requireNotNull(definitions[id])
    fun all(): List<ManagedWorkflowDefinition> = ManagedWorkflowId.entries.map(::get)
}

