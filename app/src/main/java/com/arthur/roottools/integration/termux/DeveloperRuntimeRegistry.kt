package com.arthur.roottools.integration.termux

import android.content.Context
import com.arthur.roottools.security.LocalManifestSignature
import com.arthur.roottools.security.LocalManifestSigner
import com.arthur.roottools.workflow.ManagedWorkflowCanonicalizer
import com.arthur.roottools.workflow.ManagedWorkflowCatalog
import java.io.File

data class DeveloperRuntimeArtifactDescriptor(
    val id: String,
    val version: Int,
    val sha256: String,
    val fileName: String,
)

data class DeveloperRuntimeRegistryPayload(
    val deviceId: String,
    val generatedAtEpochMs: Long,
    val artifacts: List<DeveloperRuntimeArtifactDescriptor>,
)

data class SignedDeveloperRuntimeRegistry(
    val file: File,
    val signature: LocalManifestSignature,
    val payload: String,
)

object DeveloperRuntimeRegistryCanonicalizer {
    fun canonicalPayload(payload: DeveloperRuntimeRegistryPayload): String {
        require(payload.generatedAtEpochMs > 0L) { "Invalid registry timestamp" }
        require(DEVICE_ID_REGEX.matches(payload.deviceId)) { "Invalid developer device id" }
        val artifacts = payload.artifacts.sortedBy { it.id }.joinToString(",") { artifact ->
            require(ARTIFACT_ID_REGEX.matches(artifact.id)) { "Invalid artifact id" }
            require(artifact.version > 0) { "Invalid artifact version" }
            require(SHA256_REGEX.matches(artifact.sha256)) { "Invalid artifact checksum" }
            require(FILE_NAME_REGEX.matches(artifact.fileName)) { "Invalid artifact file name" }
            buildString {
                append("{\"id\":").append(ManagedWorkflowCanonicalizer.quote(artifact.id))
                append(",\"version\":").append(artifact.version)
                append(",\"sha256\":").append(ManagedWorkflowCanonicalizer.quote(artifact.sha256))
                append(",\"fileName\":").append(ManagedWorkflowCanonicalizer.quote(artifact.fileName))
                append('}')
            }
        }
        val workflows = ManagedWorkflowCatalog.all().joinToString(",") { definition ->
            val steps = definition.steps.joinToString(",") { step ->
                ManagedWorkflowCanonicalizer.quote(step.type.name)
            }
            buildString {
                append("{\"id\":").append(ManagedWorkflowCanonicalizer.quote(definition.id.name))
                append(",\"version\":").append(definition.version)
                append(",\"requiresPackageName\":").append(definition.requiresPackageName)
                append(",\"steps\":[").append(steps).append("]}")
            }
        }
        return buildString {
            append("{\"schemaVersion\":1")
            append(",\"deviceId\":").append(ManagedWorkflowCanonicalizer.quote(payload.deviceId))
            append(",\"generatedAtEpochMs\":").append(payload.generatedAtEpochMs)
            append(",\"artifacts\":[").append(artifacts).append(']')
            append(",\"workflows\":[").append(workflows).append("]}")
        }
    }

    private val DEVICE_ID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    private val ARTIFACT_ID_REGEX = Regex("^[a-z0-9._-]{2,64}$")
    private val SHA256_REGEX = Regex("^[0-9a-f]{64}$")
    private val FILE_NAME_REGEX = Regex("^[A-Za-z0-9._-]{1,96}$")
}

class DeveloperRuntimeRegistryExporter(context: Context) {
    private val appContext = context.applicationContext
    private val cliProvisioner = TermuxCliProvisioner(appContext)
    private val mcpProvisioner = TermuxMcpRelayProvisioner(appContext)
    private val identityStore = DeveloperDeviceIdentityStore(appContext)
    private val signer = LocalManifestSigner(KEY_ALIAS)

    fun export(generatedAtEpochMs: Long = System.currentTimeMillis()): SignedDeveloperRuntimeRegistry {
        val artifacts = buildList {
            cliProvisioner.existingArtifactInfo()?.let { artifact ->
                add(
                    DeveloperRuntimeArtifactDescriptor(
                        id = "roottools-cli",
                        version = artifact.version,
                        sha256 = artifact.sha256,
                        fileName = artifact.file.name,
                    )
                )
            }
            mcpProvisioner.existingArtifactInfo()?.let { artifact ->
                add(
                    DeveloperRuntimeArtifactDescriptor(
                        id = "termux-mcp-relay",
                        version = artifact.version,
                        sha256 = artifact.sha256,
                        fileName = artifact.file.name,
                    )
                )
            }
        }
        val payload = DeveloperRuntimeRegistryCanonicalizer.canonicalPayload(
            DeveloperRuntimeRegistryPayload(
                deviceId = identityStore.deviceId,
                generatedAtEpochMs = generatedAtEpochMs,
                artifacts = artifacts,
            )
        )
        val signature = signer.sign(payload)
        val root = File(appContext.cacheDir, TermuxCliProvisioner.DIRECTORY).apply { mkdirs() }
        val file = File(root, FILE_NAME)
        file.writeText(
            buildString {
                append("{\"algorithm\":").append(ManagedWorkflowCanonicalizer.quote(signature.algorithm))
                append(",\"keyId\":").append(ManagedWorkflowCanonicalizer.quote(signature.keyId))
                append(",\"payload\":").append(payload)
                append(",\"signature\":").append(ManagedWorkflowCanonicalizer.quote(signature.signatureBase64))
                append('}')
            }
        )
        return SignedDeveloperRuntimeRegistry(file, signature, payload)
    }

    fun verify(payload: String, signature: LocalManifestSignature): Boolean = signer.verify(payload, signature)

    companion object {
        const val FILE_NAME = "roottools-runtime-registry.json"
        private const val KEY_ALIAS = "roottools.developer-runtime.registry.hmac.v1"
    }
}

