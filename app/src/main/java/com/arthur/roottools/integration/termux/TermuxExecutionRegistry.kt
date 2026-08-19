package com.arthur.roottools.integration.termux

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

internal object TermuxExecutionRegistry {
    private data class PendingExecution(
        val taskId: TermuxManagedTaskId,
        val deferred: CompletableDeferred<TermuxTaskResult>,
    )

    private val pending = ConcurrentHashMap<String, PendingExecution>()

    fun register(executionId: String, taskId: TermuxManagedTaskId): CompletableDeferred<TermuxTaskResult> {
        val deferred = CompletableDeferred<TermuxTaskResult>()
        check(pending.putIfAbsent(executionId, PendingExecution(taskId, deferred)) == null) {
            "Duplicate Termux execution id"
        }
        return deferred
    }

    fun complete(executionId: String, parser: (TermuxManagedTaskId) -> TermuxTaskResult) {
        val record = pending.remove(executionId) ?: return
        record.deferred.complete(parser(record.taskId))
    }

    fun fail(executionId: String, message: String) {
        val record = pending.remove(executionId) ?: return
        record.deferred.complete(
            TermuxTaskResult(
                executionId = executionId,
                taskId = record.taskId,
                success = false,
                stdout = "",
                stderr = "",
                exitCode = -1,
                internalError = -1,
                internalErrorMessage = "",
                stdoutOriginalLength = 0,
                stderrOriginalLength = 0,
                transportError = message,
            )
        )
    }

    fun cancel(executionId: String) {
        pending.remove(executionId)?.deferred?.cancel()
    }
}

