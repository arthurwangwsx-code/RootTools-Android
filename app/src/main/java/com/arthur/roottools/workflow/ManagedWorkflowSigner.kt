package com.arthur.roottools.workflow

import android.content.Context
import com.arthur.roottools.integration.termux.DeveloperDeviceIdentityStore
import com.arthur.roottools.security.LocalManifestSignature
import com.arthur.roottools.security.LocalManifestSigner

data class SignedManagedWorkflow(
    val payload: String,
    val algorithm: String,
    val keyId: String,
    val signatureBase64: String,
) {
    fun toJson(): String = buildString {
        append("{\"algorithm\":")
        append(ManagedWorkflowCanonicalizer.quote(algorithm))
        append(",\"keyId\":")
        append(ManagedWorkflowCanonicalizer.quote(keyId))
        append(",\"payload\":")
        append(payload)
        append(",\"signature\":")
        append(ManagedWorkflowCanonicalizer.quote(signatureBase64))
        append('}')
    }
}

/** Local approval signature. The HMAC key is non-exportable from Android Keystore. */
class ManagedWorkflowSigner(context: Context) {
    private val identityStore = DeveloperDeviceIdentityStore(context.applicationContext)
    private val signer = LocalManifestSigner(KEY_ALIAS)

    fun sign(request: ManagedWorkflowRequest, createdAtEpochMs: Long = System.currentTimeMillis()): SignedManagedWorkflow {
        val payload = ManagedWorkflowCanonicalizer.canonicalPayload(
            request = request,
            deviceId = identityStore.deviceId,
            createdAtEpochMs = createdAtEpochMs,
        )
        val signature = signer.sign(payload)
        return SignedManagedWorkflow(
            payload = payload,
            algorithm = signature.algorithm,
            keyId = signature.keyId,
            signatureBase64 = signature.signatureBase64,
        )
    }

    fun verify(signed: SignedManagedWorkflow): Boolean = signer.verify(
        signed.payload,
        LocalManifestSignature(
            algorithm = signed.algorithm,
            keyId = signed.keyId,
            signatureBase64 = signed.signatureBase64,
        )
    )

    companion object {
        private const val KEY_ALIAS = "roottools.workflow.hmac.v1"
    }
}

