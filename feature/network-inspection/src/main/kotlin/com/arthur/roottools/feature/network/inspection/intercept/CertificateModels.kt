package com.arthur.roottools.feature.network.inspection.intercept

enum class CertificateSource(val wireName: String) {
    MITM_ADDON("pcapdroid_mitm"),
    STANDALONE("roottools_standalone"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): CertificateSource = entries.firstOrNull { it.wireName == value } ?: UNKNOWN
    }
}

data class InterceptionCertificateStatus(
    val available: Boolean = false,
    val subject: String? = null,
    val fingerprint: String? = null,
    val systemModuleInstalled: Boolean = false,
    val systemTrusted: Boolean = false,
    val requiresReboot: Boolean = false,
    val certificateFile: String? = null,
    val source: CertificateSource = CertificateSource.UNKNOWN,
    val notAfter: Long? = null,
)

data class CertificateModuleCommands(
    val stagedCheck: String,
    val trustedCheck: String,
    val install: String,
    val remove: String,
)
