package com.arthur.roottools.integration.termux

import android.content.Context
import com.arthur.roottools.automation.AutomationAuthorizationPolicy
import com.arthur.roottools.automation.AutomationClientStore
import java.io.File

data class TermuxCliArtifact(
    val file: File,
    val clientId: String,
    val createdAtEpochMs: Long,
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
        )
    }

    @Synchronized
    fun revoke() {
        clientStore.revoke(AutomationClientStore.TERMUX_CLIENT_ID)
        File(File(appContext.cacheDir, DIRECTORY), FILE_NAME).delete()
    }

    fun existingArtifact(): File? = File(File(appContext.cacheDir, DIRECTORY), FILE_NAME)
        .takeIf { it.isFile && it.length() > 0L }

    companion object {
        const val DIRECTORY = "developer-runtime"
        const val FILE_NAME = "roottools"
    }
}

