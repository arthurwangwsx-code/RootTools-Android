package com.arthur.roottools.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

internal data class GoogleRevocationEntry(
    val status: String,
    val reason: String?,
)

internal data class GoogleAttestationOnlineData(
    val rootSpkiSha256: Set<String> = emptySet(),
    val revocations: Map<String, GoogleRevocationEntry> = emptyMap(),
    val rootsChecked: Boolean = false,
    val revocationsChecked: Boolean = false,
    val errors: List<String> = emptyList(),
)

/**
 * On-demand client for Google's public Android Key Attestation metadata.
 *
 * This is intentionally not a daemon/cache warmer. Integrity scans can run fully offline; these
 * endpoints only refresh trust anchors and the revocation list when the user explicitly scans.
 */
class GoogleAttestationStatusClient {
    internal suspend fun fetch(): GoogleAttestationOnlineData = withContext(Dispatchers.IO) {
        var roots = emptySet<String>()
        var revocations = emptyMap<String, GoogleRevocationEntry>()
        var rootsChecked = false
        var revocationsChecked = false
        val errors = mutableListOf<String>()

        runCatching { fetchRoots() }
            .onSuccess {
                roots = it
                rootsChecked = true
            }
            .onFailure { errors += "Google roots: ${it.message ?: it.javaClass.simpleName}" }

        runCatching { fetchRevocations() }
            .onSuccess {
                revocations = it
                revocationsChecked = true
            }
            .onFailure { errors += "Google revocation: ${it.message ?: it.javaClass.simpleName}" }

        GoogleAttestationOnlineData(
            rootSpkiSha256 = roots,
            revocations = revocations,
            rootsChecked = rootsChecked,
            revocationsChecked = revocationsChecked,
            errors = errors,
        )
    }

    private fun fetchRoots(): Set<String> {
        val text = get(ROOTS_URL)
        val array = JSONArray(text)
        val factory = CertificateFactory.getInstance("X.509")
        return buildSet {
            for (index in 0 until array.length()) {
                val pem = array.getString(index)
                val cert = factory.generateCertificate(ByteArrayInputStream(pem.toByteArray())) as X509Certificate
                add(AttestationChainVerifier.sha256(cert.publicKey.encoded))
            }
        }
    }

    private fun fetchRevocations(): Map<String, GoogleRevocationEntry> {
        val entries = JSONObject(get(STATUS_URL)).getJSONObject("entries")
        return buildMap {
            val keys = entries.keys()
            while (keys.hasNext()) {
                val rawKey = keys.next()
                val value = entries.getJSONObject(rawKey)
                val key = normalizeSerial(rawKey)
                put(
                    key,
                    GoogleRevocationEntry(
                        status = value.optString("status", "unknown"),
                        reason = value.optString("reason").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "RootTools-KeyAttestation/0.2")
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val ROOTS_URL = "https://android.googleapis.com/attestation/root"
        private const val STATUS_URL = "https://android.googleapis.com/attestation/status"
        private const val TIMEOUT_MS = 4_000

        internal fun normalizeSerial(value: String): String =
            value.trim().lowercase().removePrefix("0x").trimStart('0').ifBlank { "0" }
    }
}
