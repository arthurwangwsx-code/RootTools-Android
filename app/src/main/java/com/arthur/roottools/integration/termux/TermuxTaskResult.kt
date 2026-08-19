package com.arthur.roottools.integration.termux

data class TermuxTaskResult(
    val executionId: String,
    val taskId: TermuxManagedTaskId,
    val success: Boolean,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val internalError: Int,
    val internalErrorMessage: String,
    val stdoutOriginalLength: Int,
    val stderrOriginalLength: Int,
    val transportError: String? = null,
) {
    val truncatedByTermux: Boolean
        get() = stdoutOriginalLength > stdout.length || stderrOriginalLength > stderr.length
}

