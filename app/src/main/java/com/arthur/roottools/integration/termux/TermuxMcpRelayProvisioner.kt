package com.arthur.roottools.integration.termux

import android.content.Context
import androidx.core.content.edit
import com.arthur.roottools.automation.AutomationAuthorizationPolicy
import com.arthur.roottools.automation.AutomationClientStore
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class TermuxMcpRelayArtifact(
    val file: File,
    val version: Int,
    val deviceId: String,
    val bearerToken: String,
    val sha256: String,
    val createdAtEpochMs: Long,
)

class TermuxMcpRelayProvisioner(context: Context) {
    private val appContext = context.applicationContext
    private val clientStore = AutomationClientStore(appContext)
    private val identityStore = DeveloperDeviceIdentityStore(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun provision(): TermuxMcpRelayArtifact {
        val client = clientStore.provision(
            clientId = AutomationClientStore.TERMUX_MCP_CLIENT_ID,
            displayName = "Termux MCP Relay",
            scopes = AutomationAuthorizationPolicy.termuxMcpScopes,
        )
        val bearer = randomToken()
        val deviceId = identityStore.deviceId
        val root = File(appContext.cacheDir, TermuxCliProvisioner.DIRECTORY).apply { mkdirs() }
        val file = File(root, FILE_NAME)
        file.writeText(
            TermuxMcpRelayScriptBuilder.build(
                deviceId = deviceId,
                rootToolsAutomationToken = client.token,
                relayBearerToken = bearer,
            )
        )
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
        val createdAt = System.currentTimeMillis()
        prefs.edit {
            putString(KEY_BEARER, bearer)
            putLong(KEY_CREATED_AT, createdAt)
        }
        return artifact(file, bearer, createdAt)
    }

    fun existingArtifactInfo(): TermuxMcpRelayArtifact? {
        val bearer = prefs.getString(KEY_BEARER, null)?.takeIf(TOKEN_REGEX::matches) ?: return null
        val file = File(File(appContext.cacheDir, TermuxCliProvisioner.DIRECTORY), FILE_NAME)
            .takeIf { it.isFile && it.length() > 0L } ?: return null
        val client = clientStore.listClients().firstOrNull {
            it.clientId == AutomationClientStore.TERMUX_MCP_CLIENT_ID && !it.revoked
        } ?: return null
        return artifact(file, bearer, prefs.getLong(KEY_CREATED_AT, client.createdAtEpochMs))
    }

    @Synchronized
    fun revoke() {
        clientStore.revoke(AutomationClientStore.TERMUX_MCP_CLIENT_ID)
        prefs.edit { remove(KEY_BEARER); remove(KEY_CREATED_AT) }
        File(File(appContext.cacheDir, TermuxCliProvisioner.DIRECTORY), FILE_NAME).delete()
    }

    private fun artifact(file: File, bearer: String, createdAt: Long) = TermuxMcpRelayArtifact(
        file = file,
        version = TermuxMcpRelayScriptBuilder.VERSION,
        deviceId = identityStore.deviceId,
        bearerToken = bearer,
        sha256 = sha256(file.readBytes()),
        createdAtEpochMs = createdAt,
    )

    private fun randomToken(): String = buildString {
        append(UUID.randomUUID().toString().replace("-", ""))
        append(UUID.randomUUID().toString().replace("-", ""))
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val FILE_NAME = "roottools_mcp.py"
        private const val PREFS = "termux_mcp_relay"
        private const val KEY_BEARER = "bearer"
        private const val KEY_CREATED_AT = "created_at"
        private val TOKEN_REGEX = Regex("^[A-Za-z0-9_-]{48,128}$")
    }
}

