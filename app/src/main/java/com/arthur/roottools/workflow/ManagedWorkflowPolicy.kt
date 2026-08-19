package com.arthur.roottools.workflow

data class ManagedWorkflowValidation(
    val valid: Boolean,
    val message: String,
)

object ManagedWorkflowPolicy {
    private const val MAX_STEPS = 16
    private val packageRegex = Regex("^[A-Za-z0-9._]{1,200}$")

    fun validate(request: ManagedWorkflowRequest): ManagedWorkflowValidation {
        val definition = ManagedWorkflowCatalog.get(request.workflowId)
        if (definition.version <= 0) return invalid("Workflow version must be positive")
        if (definition.steps.isEmpty() || definition.steps.size > MAX_STEPS) {
            return invalid("Workflow step count is outside the supported range")
        }
        if (definition.requiresPackageName) {
            val packageName = request.packageName
            if (packageName == null || !packageRegex.matches(packageName)) {
                return invalid("Workflow requires a valid Android package name")
            }
        } else if (request.packageName != null) {
            return invalid("This workflow does not accept a package input")
        }

        var diagnosticAvailable = false
        definition.steps.forEach { step ->
            when (step.type) {
                ManagedWorkflowStepType.SET_PERFORMANCE_MODE -> {
                    if (step.performanceMode == null) return invalid("Performance step is missing a mode")
                }
                ManagedWorkflowStepType.RUN_DIAGNOSTIC -> diagnosticAvailable = true
                ManagedWorkflowStepType.TERMUX_POST_PROCESS_DIAGNOSTIC -> {
                    if (!diagnosticAvailable) return invalid("Diagnostic post-processing requires an earlier diagnostic step")
                }
                else -> {
                    if (step.performanceMode != null) return invalid("Unexpected performance mode on non-performance step")
                }
            }
        }
        return ManagedWorkflowValidation(true, "OK")
    }

    private fun invalid(message: String) = ManagedWorkflowValidation(false, message)
}

