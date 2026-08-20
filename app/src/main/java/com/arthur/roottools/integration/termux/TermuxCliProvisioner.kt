package com.arthur.roottools.integration.termux

import android.content.Context
import com.arthur.roottools.automation.AutomationAuthorizationPolicy
import com.arthur.roottools.automation.AutomationClientStore
import java.io.File
import java.security.MessageDigest

data class TermuxCliArtifact(
    val file: File,
    val clientId: String,
    val createdAtEpochMs: Long,
    val version: Int,
    val sha256: String,
)

class TermuxCliProvisioner(context: Context) {
    private val appContext = context.applicationContext
    private val clientStore = AutomationClientStore(appContext)

    @Synchronized
    fun provision(): TermuxCliArtifact {
        val provisioned = clientStore.provision(
            clientId = AutomationClientStore.TERMUX_CLIENT_ID,
            displayName = "Termux CLI",
            scopes = AutomationAuthorizationPolicy.termuxDefaultScopes,
        )
        val root = File(appContext.cacheDir, DIRECTORY).apply { mkdirs() }
        val file = File(root, FILE_NAME)
        file.writeText(TermuxCliScriptBuilder.build(provisioned.token))
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
        return TermuxCliArtifact(
            file = file,
            clientId = provisioned.record.clientId,
            createdAtEpochMs = provisioned.record.createdAtEpochMs,
            version = TermuxCliScriptBuilder.VERSION,
            sha256 = sha256(file.readBytes()),
        )
    }

    @Synchronized
    fun revoke() {
        clientStore.revoke(AutomationClientStore.TERMUX_CLIENT_ID)
        File(File(appContext.cacheDir, DIRECTORY), FILE_NAME).delete()
    }

    fun existingArtifact(): File? = File(File(appContext.cacheDir, DIRECTORY), FILE_NAME)
        .takeIf { it.isFile && it.length() > 0L }

    fun existingArtifactInfo(): TermuxCliArtifact? {
        val file = existingArtifact() ?: return null
        val client = clientStore.listClients().firstOrNull {
            it.clientId == AutomationClientStore.TERMUX_CLIENT_ID && !it.revoked
        } ?: return null
        return TermuxCliArtifact(
            file = file,
            clientId = client.clientId,
            createdAtEpochMs = client.createdAtEpochMs,
            version = TermuxCliScriptBuilder.VERSION,
            sha256 = sha256(file.readBytes()),
        )
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val DIRECTORY = "developer-runtime"
        const val FILE_NAME = "roottools"
    }
}

