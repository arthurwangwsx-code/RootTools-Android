package com.arthur.roottools.automation

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class AutomationClientRecord(
    val clientId: String,
    val displayName: String,
    val tokenHash: String,
    val scopes: Set<AutomationScope>,
    val createdAtEpochMs: Long,
    val lastUsedAtEpochMs: Long?,
    val revoked: Boolean,
)

data class ProvisionedAutomationClient(
    val record: AutomationClientRecord,
    val token: String,
)

class AutomationClientStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun provision(
        clientId: String,
        displayName: String,
        scopes: Set<AutomationScope>,
    ): ProvisionedAutomationClient {
        require(CLIENT_ID_REGEX.matches(clientId)) { "Invalid automation client id" }
        require(displayName.isNotBlank() && displayName.length <= 60) { "Invalid automation client name" }
        require(scopes.isNotEmpty()) { "Automation client requires at least one scope" }

        val token = buildString {
            append(UUID.randomUUID().toString().replace("-", ""))
            append(UUID.randomUUID().toString().replace("-", ""))
        }
        val now = System.currentTimeMillis()
        val record = AutomationClientRecord(
            clientId = clientId,
            displayName = displayName,
            tokenHash = hashToken(token),
            scopes = scopes,
            createdAtEpochMs = now,
            lastUsedAtEpochMs = null,
            revoked = false,
        )
        val updated = readAll().filterNot { it.clientId == clientId } + record
        writeAll(updated)
        return ProvisionedAutomationClient(record, token)
    }

    @Synchronized
    fun authorize(token: String?, command: AutomationCommand, enabled: Boolean? = null): AutomationClientRecord? {
        if (token.isNullOrBlank() || token.length < MIN_TOKEN_LENGTH) return null
        val candidateHash = hashToken(token)
        val record = readAll().firstOrNull { client ->
            !client.revoked && secureEquals(client.tokenHash, candidateHash)
        } ?: return null
        if (!AutomationAuthorizationPolicy.isAllowed(record.scopes, command, enabled)) return null
        markUsed(record.clientId)
        return record.copy(lastUsedAtEpochMs = System.currentTimeMillis())
    }

    @Synchronized
    fun listClients(): List<AutomationClientRecord> = readAll()

    @Synchronized
    fun revoke(clientId: String) {
        writeAll(readAll().map { client ->
            if (client.clientId == clientId) client.copy(revoked = true) else client
        })
    }

    @Synchronized
    fun remove(clientId: String) {
        writeAll(readAll().filterNot { it.clientId == clientId })
    }

    private fun markUsed(clientId: String) {
        val now = System.currentTimeMillis()
        writeAll(readAll().map { client ->
            if (client.clientId == clientId) client.copy(lastUsedAtEpochMs = now) else client
        })
    }

    private fun readAll(): List<AutomationClientRecord> {
        val raw = prefs.getString(KEY_CLIENTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val clientId = obj.optString("clientId")
                    val displayName = obj.optString("displayName")
                    val tokenHash = obj.optString("tokenHash")
                    if (!CLIENT_ID_REGEX.matches(clientId) || displayName.isBlank() || tokenHash.length != 64) continue
                    val scopeArray = obj.optJSONArray("scopes") ?: JSONArray()
                    val scopes = buildSet {
                        for (scopeIndex in 0 until scopeArray.length()) {
                            runCatching { AutomationScope.valueOf(scopeArray.optString(scopeIndex)) }
                                .getOrNull()
                                ?.let(::add)
                        }
                    }
                    if (scopes.isEmpty()) continue
                    add(
                        AutomationClientRecord(
                            clientId = clientId,
                            displayName = displayName,
                            tokenHash = tokenHash,
                            scopes = scopes,
                            createdAtEpochMs = obj.optLong("createdAt", 0L),
                            lastUsedAtEpochMs = obj.optLong("lastUsedAt", 0L).takeIf { it > 0L },
                            revoked = obj.optBoolean("revoked", false),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAll(clients: List<AutomationClientRecord>) {
        val array = JSONArray()
        clients.forEach { client ->
            array.put(
                JSONObject()
                    .put("clientId", client.clientId)
                    .put("displayName", client.displayName)
                    .put("tokenHash", client.tokenHash)
                    .put("scopes", JSONArray(client.scopes.map { it.name }))
                    .put("createdAt", client.createdAtEpochMs)
                    .put("lastUsedAt", client.lastUsedAtEpochMs ?: 0L)
                    .put("revoked", client.revoked)
            )
        }
        prefs.edit { putString(KEY_CLIENTS, array.toString()) }
    }

    private fun hashToken(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun secureEquals(leftHex: String, rightHex: String): Boolean = MessageDigest.isEqual(
        leftHex.toByteArray(Charsets.US_ASCII),
        rightHex.toByteArray(Charsets.US_ASCII),
    )

    companion object {
        const val TERMUX_CLIENT_ID = "termux"
        private const val PREFS_NAME = "automation_clients"
        private const val KEY_CLIENTS = "clients_v1"
        private const val MIN_TOKEN_LENGTH = 48
        private val CLIENT_ID_REGEX = Regex("^[a-z0-9][a-z0-9._-]{1,39}$")
    }
}

