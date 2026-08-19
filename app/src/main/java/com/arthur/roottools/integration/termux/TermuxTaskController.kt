package com.arthur.roottools.integration.termux

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** Typed policy boundary for every RootTools -> Termux execution. */
class TermuxTaskController(context: Context) {
    private val appContext = context.applicationContext
    private val backend = OfficialTermuxRunCommandBackend(appContext)
    private val audit = TermuxTaskAuditStore(appContext)
    private val cliProvisioner = TermuxCliProvisioner(appContext)
    private val mcpProvisioner = TermuxMcpRelayProvisioner(appContext)

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

    suspend fun installGeneratedMcpRelay(): TermuxTaskResult = withContext(Dispatchers.IO) {
        val artifact = mcpProvisioner.existingArtifactInfo()
            ?: return@withContext localFailure(
                TermuxManagedTaskId.INSTALL_MCP_RELAY,
                "Generate the RootTools MCP relay artifact first",
            )
        val script = runCatching { artifact.file.readText() }.getOrElse {
            return@withContext localFailure(
                TermuxManagedTaskId.INSTALL_MCP_RELAY,
                "Unable to read RootTools MCP relay artifact",
            )
        }
        val result = execute(TermuxManagedTaskId.INSTALL_MCP_RELAY, script)
        if (!result.success) return@withContext result
        val reportedHash = parseKeyValue(result.stdout)["sha256"]
        if (reportedHash != artifact.sha256) {
            return@withContext result.copy(
                success = false,
                transportError = "Installed MCP relay checksum does not match the RootTools artifact",
            )
        }
        result
    }

    suspend fun verifyGeneratedMcpRelay(): TermuxMcpRelayVerification {
        val artifact = withContext(Dispatchers.IO) { mcpProvisioner.existingArtifactInfo() }
        val result = execute(TermuxManagedTaskId.VERIFY_MCP_RELAY)
        val values = parseKeyValue(result.stdout)
        val remoteHash = values["sha256"]
        return TermuxMcpRelayVerification(
            result = result,
            installed = values["installed"] == "1",
            installedSha256 = remoteHash,
            expectedSha256 = artifact?.sha256,
            matchesGeneratedArtifact = result.success && artifact != null && remoteHash == artifact.sha256,
        )
    }

    suspend fun postProcessDiagnostic(snapshotText: String): TermuxTaskResult {
        if (snapshotText.isBlank()) {
            return localFailure(TermuxManagedTaskId.POST_PROCESS_DIAGNOSTIC, "Diagnostic snapshot is empty")
        }
        return execute(TermuxManagedTaskId.POST_PROCESS_DIAGNOSTIC, snapshotText)
    }

    suspend fun importBackupArtifact(artifactId: String, file: File): TermuxBackupHandoffResult = withContext(Dispatchers.IO) {
        val canonicalFile = runCatching { file.canonicalFile }.getOrElse {
            return@withContext TermuxBackupHandoffResult(false, artifactId, null, null, 0, "Unable to resolve backup artifact")
        }
        val validation = TermuxBackupHandoffPolicy.validateMetadata(
            artifactId = artifactId,
            fileName = canonicalFile.name,
            length = canonicalFile.length(),
        )
        if (!canonicalFile.isFile || !validation.valid) {
            return@withContext TermuxBackupHandoffResult(false, artifactId, null, null, 0, validation.message)
        }
        val roots = listOfNotNull(
            appContext.filesDir,
            appContext.cacheDir,
            appContext.getExternalFilesDir(null),
        ).mapNotNull { runCatching { it.canonicalPath }.getOrNull() }
        if (!TermuxBackupHandoffPolicy.isAppOwnedPath(canonicalFile.canonicalPath, roots)) {
            return@withContext TermuxBackupHandoffResult(false, artifactId, null, null, 0, "Backup source is outside RootTools-owned storage")
        }

        val digest = MessageDigest.getInstance("SHA-256")
        var offset = 0L
        var chunks = 0
        FileInputStream(canonicalFile).use { input ->
            val buffer = ByteArray(TermuxBackupHandoffPolicy.CHUNK_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
                val encoded = Base64.encodeToString(buffer.copyOf(read), Base64.NO_WRAP)
                val payload = JSONObject()
                    .put("artifactId", artifactId)
                    .put("offset", offset)
                    .put("data", encoded)
                    .toString()
                val result = execute(TermuxManagedTaskId.BACKUP_IMPORT_CHUNK, payload)
                if (!result.success) {
                    return@withContext TermuxBackupHandoffResult(
                        false,
                        artifactId,
                        null,
                        null,
                        chunks,
                        result.transportError ?: result.internalErrorMessage.ifBlank { result.stderr.ifBlank { "Backup chunk import failed" } },
                    )
                }
                offset += read
                chunks++
            }
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        val finalizePayload = JSONObject()
            .put("artifactId", artifactId)
            .put("sha256", sha256)
            .put("fileName", canonicalFile.name)
            .toString()
        val finalize = execute(TermuxManagedTaskId.BACKUP_FINALIZE_IMPORT, finalizePayload)
        val values = if (finalize.success) parseJsonObject(finalize.stdout) else emptyMap()
        TermuxBackupHandoffResult(
            success = finalize.success,
            artifactId = artifactId,
            remotePath = values["path"],
            sha256 = sha256,
            chunks = chunks,
            message = if (finalize.success) "Backup artifact transferred and verified" else finalize.transportError
                ?: finalize.internalErrorMessage.ifBlank { finalize.stderr.ifBlank { "Backup finalize failed" } },
        )
    }

    suspend fun createBackupArchive(): TermuxTaskResult = execute(TermuxManagedTaskId.BACKUP_CREATE_ARCHIVE)

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

        internal fun parseJsonObject(raw: String): Map<String, String> = runCatching {
            val json = JSONObject(raw.trim())
            json.keys().asSequence().associateWith { key -> json.opt(key)?.toString().orEmpty() }
        }.getOrDefault(emptyMap())
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

data class TermuxMcpRelayVerification(
    val result: TermuxTaskResult,
    val installed: Boolean,
    val installedSha256: String?,
    val expectedSha256: String?,
    val matchesGeneratedArtifact: Boolean,
)

data class TermuxBackupHandoffResult(
    val success: Boolean,
    val artifactId: String,
    val remotePath: String?,
    val sha256: String?,
    val chunks: Int,
    val message: String,
)

