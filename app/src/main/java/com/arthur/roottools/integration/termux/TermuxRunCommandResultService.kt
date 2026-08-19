package com.arthur.roottools.integration.termux

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Receives one-shot RUN_COMMAND results through the PendingIntent supplied to Termux. */
class TermuxRunCommandResultService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val executionId = intent?.getStringExtra(EXTRA_EXECUTION_ID)
        if (!executionId.isNullOrBlank()) {
            val bundle = intent.getBundleExtra(TermuxRunCommandContract.EXTRA_RESULT_BUNDLE)
            if (bundle == null) {
                TermuxExecutionRegistry.fail(executionId, "Termux result bundle is missing")
            } else {
                TermuxExecutionRegistry.complete(executionId) { taskId ->
                    val stdout = bundle.getString(TermuxRunCommandContract.RESULT_STDOUT, "")
                    val stderr = bundle.getString(TermuxRunCommandContract.RESULT_STDERR, "")
                    val internalError = bundle.getInt(TermuxRunCommandContract.RESULT_INTERNAL_ERROR, 0)
                    val exitCode = bundle.getInt(TermuxRunCommandContract.RESULT_EXIT_CODE, -1)
                    TermuxTaskResult(
                        executionId = executionId,
                        taskId = taskId,
                        success = internalError == -1 && exitCode == 0,
                        stdout = stdout,
                        stderr = stderr,
                        exitCode = exitCode,
                        internalError = internalError,
                        internalErrorMessage = bundle.getString(TermuxRunCommandContract.RESULT_INTERNAL_ERROR_MESSAGE, ""),
                        stdoutOriginalLength = bundle.getInt(
                            TermuxRunCommandContract.RESULT_STDOUT_ORIGINAL_LENGTH,
                            stdout.length,
                        ),
                        stderrOriginalLength = bundle.getInt(
                            TermuxRunCommandContract.RESULT_STDERR_ORIGINAL_LENGTH,
                            stderr.length,
                        ),
                    )
                }
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_EXECUTION_ID = "termux_execution_id"
    }
}

