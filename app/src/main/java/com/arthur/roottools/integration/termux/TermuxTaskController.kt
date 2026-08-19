package com.arthur.roottools.integration.termux

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Typed policy boundary for every RootTools -> Termux execution. */
class TermuxTaskController(context: Context) {
    private val appContext = context.applicationContext
    private val backend = OfficialTermuxRunCommandBackend(appContext)
    private val audit = TermuxTaskAuditStore(appContext)
    private val cliProvisioner = TermuxCliProvisioner(appContext)

    suspend fun run(taskId: TermuxManagedTaskId): TermuxTaskResult = execute(taskId)

    suspend fun installGeneratedCli(): TermuxTaskResult = withContext(Dispatchers.IO) {
        val artifact = cliProvisioner.existingArtifactInfo()
            ?: return@withContext localFailure(
                TermuxManagedTaskId.INSTALL_ROOTTOOLS_CLI,
                "Generate the RootTools CLI artifact first",
            )
        val script = runCatching { artifact.file.readText() }.getOrElse {
            return@withContext localFailure(
                TermuxManagedTaskId.INSTALL_ROOTTOOLS_CLI,
                "Unable to read RootTools CLI artifact",
            )
        }
        val result = execute(TermuxManagedTaskId.INSTALL_ROOTTOOLS_CLI, script)
        if (!result.success) return@withContext result

        val reportedHash = parseKeyValue(result.stdout)["sha256"]
        if (reportedHash != artifact.sha256) {
            return@withContext result.copy(
                success = false,
                transportError = "Installed CLI checksum does not match the RootTools artifact",
            )
        }
        result
    }

    suspend fun verifyGeneratedCli(): TermuxCliVerification {
        val artifact = withContext(Dispatchers.IO) { cliProvisioner.existingArtifactInfo() }
        val result = execute(TermuxManagedTaskId.VERIFY_ROOTTOOLS_CLI)
        val values = parseKeyValue(result.stdout)
        val remoteHash = values["sha256"]
        return TermuxCliVerification(
            result = result,
            installed = values["installed"] == "1",
            mode = values["mode"],
            installedSha256 = remoteHash,
            expectedSha256 = artifact?.sha256,
            matchesGeneratedArtifact = result.success && artifact != null && remoteHash == artifact.sha256,
        )
    }

    fun readAudit(limit: Int = 30): List<TermuxTaskAuditRecord> = audit.read(limit)

    private suspend fun execute(
        taskId: TermuxManagedTaskId,
        rootToolsStdin: String? = null,
    ): TermuxTaskResult {
        val started = System.currentTimeMillis()
        val result = backend.execute(taskId, rootToolsStdin)
        val duration = (System.currentTimeMillis() - started).coerceAtLeast(0L)
        val spec = TermuxManagedTaskRegistry.spec(taskId)
        withContext(Dispatchers.IO) {
            audit.record(
                TermuxTaskAuditRecord(
                    timestampMs = started,
                    taskId = taskId,
                    mutation = spec.mutation,
                    success = result.success,
                    exitCode = result.exitCode,
                    durationMs = duration,
                    truncated = result.truncatedByTermux,
                )
            )
        }
        return result
    }

    private fun localFailure(taskId: TermuxManagedTaskId, message: String) = TermuxTaskResult(
        executionId = "local",
        taskId = taskId,
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

    companion object {
        internal fun parseKeyValue(raw: String): Map<String, String> = raw.lineSequence()
            .mapNotNull { line ->
                val key = line.substringBefore('=', "").trim()
                val value = line.substringAfter('=', "").trim()
                if (key.matches(Regex("[a-zA-Z0-9_.-]{1,64}")) && value.length <= 512) key to value else null
            }
            .toMap()
    }
}

data class TermuxCliVerification(
    val result: TermuxTaskResult,
    val installed: Boolean,
    val mode: String?,
    val installedSha256: String?,
    val expectedSha256: String?,
    val matchesGeneratedArtifact: Boolean,
)

