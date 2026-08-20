package com.arthur.roottools.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

data class LocalManifestSignature(
    val algorithm: String,
    val keyId: String,
    val signatureBase64: String,
)

/** Non-exportable per-install HMAC signer for locally approved manifests and registries. */
class LocalManifestSigner(private val keyAlias: String) {
    init {
        require(KEY_ALIAS_REGEX.matches(keyAlias)) { "Invalid manifest signing key alias" }
    }

    fun sign(payload: String): LocalManifestSignature {
        require(payload.length in 1..MAX_PAYLOAD_CHARS) { "Manifest payload is outside size limits" }
        val signature = computeMac(payload.toByteArray(Charsets.UTF_8))
        return LocalManifestSignature(
            algorithm = ALGORITHM,
            keyId = keyAlias,
            signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP),
        )
    }

    fun verify(payload: String, signature: LocalManifestSignature): Boolean {
        if (signature.algorithm != ALGORITHM || signature.keyId != keyAlias) return false
        if (payload.length !in 1..MAX_PAYLOAD_CHARS) return false
        val candidate = runCatching { Base64.decode(signature.signatureBase64, Base64.NO_WRAP) }.getOrNull()
            ?: return false
        return MessageDigest.isEqual(computeMac(payload.toByteArray(Charsets.UTF_8)), candidate)
    }

    private fun computeMac(payload: ByteArray): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(getOrCreateKey())
        return mac.doFinal(payload)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALGORITHM = "HmacSHA256"
        private const val MAX_PAYLOAD_CHARS = 256_000
        private val KEY_ALIAS_REGEX = Regex("^[A-Za-z0-9._-]{3,80}$")
    }
}
