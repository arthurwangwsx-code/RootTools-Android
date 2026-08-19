package com.arthur.roottools.integration.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class OfficialTermuxRunCommandBackend(context: Context) {
    private val appContext = context.applicationContext

    suspend fun execute(taskId: TermuxManagedTaskId): TermuxTaskResult {
        val spec = TermuxManagedTaskRegistry.spec(taskId)
        val executionId = UUID.randomUUID().toString()
        val deferred = TermuxExecutionRegistry.register(executionId, taskId)

        try {
            val resultIntent = Intent(appContext, TermuxRunCommandResultService::class.java)
                .putExtra(TermuxRunCommandResultService.EXTRA_EXECUTION_ID, executionId)
            val pendingIntent = PendingIntent.getService(
                appContext,
                nextRequestCode.getAndIncrement(),
                resultIntent,
                PendingIntent.FLAG_ONE_SHOT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                },
            )

            val commandIntent = Intent().apply {
                setClassName(
                    TermuxRunCommandContract.PACKAGE_NAME,
                    TermuxRunCommandContract.SERVICE_CLASS_NAME,
                )
                action = TermuxRunCommandContract.ACTION_RUN_COMMAND
                putExtra(TermuxRunCommandContract.EXTRA_COMMAND_PATH, spec.executable)
                putExtra(TermuxRunCommandContract.EXTRA_ARGUMENTS, spec.arguments.toTypedArray())
                putExtra(TermuxRunCommandContract.EXTRA_WORKDIR, spec.workDir)
                putExtra(TermuxRunCommandContract.EXTRA_BACKGROUND, true)
                putExtra(TermuxRunCommandContract.EXTRA_COMMAND_LABEL, spec.label)
                putExtra(TermuxRunCommandContract.EXTRA_COMMAND_DESCRIPTION, spec.description)
                putExtra(TermuxRunCommandContract.EXTRA_PENDING_INTENT, pendingIntent)
            }

            try {
                appContext.startService(commandIntent)
            } catch (error: Exception) {
                TermuxExecutionRegistry.fail(
                    executionId,
                    "Unable to start Termux RUN_COMMAND: ${error.message?.take(160) ?: error.javaClass.simpleName}",
                )
            }

            val raw = withTimeout(spec.timeoutMs) { deferred.await() }
            return raw.copy(
                stdout = raw.stdout.take(spec.maxOutputChars),
                stderr = raw.stderr.take(spec.maxOutputChars),
            )
        } catch (error: Exception) {
            return TermuxTaskResult(
                executionId = executionId,
                taskId = taskId,
                success = false,
                stdout = "",
                stderr = "",
                exitCode = -1,
                internalError = -1,
                internalErrorMessage = "",
                stdoutOriginalLength = 0,
                stderrOriginalLength = 0,
                transportError = when (error) {
                    is kotlinx.coroutines.TimeoutCancellationException -> "Termux task timed out after ${spec.timeoutMs} ms"
                    else -> error.message?.take(180) ?: error.javaClass.simpleName
                },
            )
        } finally {
            TermuxExecutionRegistry.cancel(executionId)
        }
    }

    companion object {
        private val nextRequestCode = AtomicInteger(20_000)
    }
}

