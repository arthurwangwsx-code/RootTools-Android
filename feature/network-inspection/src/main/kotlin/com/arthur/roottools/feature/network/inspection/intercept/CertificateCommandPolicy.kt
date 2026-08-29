package com.arthur.roottools.feature.network.inspection.intercept

object CertificateCommandPolicy {
    fun commands(subjectHash: String, stagedCertificate: String): CertificateModuleCommands? {
        if (!SUBJECT_HASH.matches(subjectHash)) return null
        val staging = safeAbsolutePath(stagedCertificate) ?: return null
        val canonicalCertificate = "$CANONICAL_MODULE/system/etc/security/cacerts/$subjectHash.0"
        val legacyCertificate = "$LEGACY_MODULE/system/etc/security/cacerts/$subjectHash.0"
        return CertificateModuleCommands(
            stagedCheck = "test -f ${quote(canonicalCertificate)} || test -f ${quote(legacyCertificate)}",
            trustedCheck = "test -f ${quote("/system/etc/security/cacerts/$subjectHash.0")} || " +
                "test -f ${quote("/apex/com.android.conscrypt/cacerts/$subjectHash.0")}",
            install = buildString {
                appendLine("mkdir -p ${quote("$CANONICAL_MODULE/system/etc/security/cacerts")}")
                appendLine("printf '%b\\n' ${quote(MODULE_PROPERTIES)} > ${quote("$CANONICAL_MODULE/module.prop")}")
                appendLine("cp ${quote(staging)} ${quote(canonicalCertificate)}")
                appendLine("chmod 0644 ${quote(canonicalCertificate)}")
                append("chown 0:0 ${quote(canonicalCertificate)}")
            },
            remove = removeModules(),
        )
    }

    fun removeModules(): String =
        "rm -r -- ${quote(CANONICAL_MODULE)} 2>/dev/null || true\n" +
            "rm -r -- ${quote(LEGACY_MODULE)} 2>/dev/null || true"

    private fun safeAbsolutePath(value: String): String? = value.takeIf {
        it.startsWith('/') && '\u0000' !in it && '\n' !in it && '\r' !in it
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private const val CANONICAL_MODULE = "/data/adb/modules/roottools_network_ca"
    private const val LEGACY_MODULE = "/data/adb/modules/nettools_ca"
    private const val MODULE_PROPERTIES = "id=roottools_network_ca\\nname=RootTools Network Inspection CA\\nversion=1.0\\nversionCode=1\\nauthor=RootTools\\ndescription=Reversible system trust overlay for authorized local traffic inspection"
    private val SUBJECT_HASH = Regex("[0-9a-f]{8}")
}
