package com.arthur.roottools.workflow

import android.content.Context
import com.arthur.roottools.integration.termux.TermuxCliProvisioner
import java.io.File

data class ManagedWorkflowManifestArtifact(
    val file: File,
    val signed: SignedManagedWorkflow,
)

class ManagedWorkflowManifestExporter(context: Context) {
    private val appContext = context.applicationContext
    private val signer = ManagedWorkflowSigner(appContext)

    fun export(request: ManagedWorkflowRequest): ManagedWorkflowManifestArtifact {
        val validation = ManagedWorkflowPolicy.validate(request)
        require(validation.valid) { validation.message }
        val signed = signer.sign(request)
        check(signer.verify(signed)) { "Workflow manifest self-verification failed" }
        val root = File(appContext.cacheDir, "${TermuxCliProvisioner.DIRECTORY}/workflows").apply { mkdirs() }
        val suffix = request.packageName?.replace('.', '_')?.take(80)?.let { "-$it" }.orEmpty()
        val file = File(root, "${request.workflowId.name.lowercase()}$suffix.json")
        file.writeText(signed.toJson())
        return ManagedWorkflowManifestArtifact(file, signed)
    }
}
