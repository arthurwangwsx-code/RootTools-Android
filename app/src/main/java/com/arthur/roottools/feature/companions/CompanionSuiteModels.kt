package com.arthur.roottools.feature.companions

enum class CompanionToolId {
    BACKGROUND_SERVER,
    HYPEROS_CREDENTIAL_FIX,
    NFC_LAB,
}

enum class CompanionRole {
    CORE,
    DEVICE_SPECIFIC,
    OPTIONAL,
}

data class CompanionToolSpec(
    val id: CompanionToolId,
    val packageName: String,
    val gradleModule: String,
    val artifactName: String,
    val role: CompanionRole,
)

data class CompanionPackageObservation(
    val installed: Boolean,
    val enabled: Boolean = false,
    val launchable: Boolean = false,
    val versionName: String? = null,
)

enum class CompanionAvailability {
    MISSING,
    DISABLED,
    INSTALLED,
}

data class CompanionToolState(
    val spec: CompanionToolSpec,
    val availability: CompanionAvailability,
    val launchable: Boolean,
    val versionName: String?,
)

data class CompanionSuiteUiState(
    val loading: Boolean = true,
    val tools: List<CompanionToolState> = emptyList(),
) {
    val installedCount: Int get() = tools.count { it.availability != CompanionAvailability.MISSING }
}

object CompanionSuiteRegistry {
    val tools = listOf(
        CompanionToolSpec(
            CompanionToolId.BACKGROUND_SERVER,
            "com.aibox.backgroundserver",
            ":companion:background-server",
            "background-server-release.apk",
            CompanionRole.CORE,
        ),
        CompanionToolSpec(
            CompanionToolId.HYPEROS_CREDENTIAL_FIX,
            "com.arthur.hyperos.credentialfix",
            ":companion:hyperos-credential-fix",
            "hyperos-credential-fix-release.apk",
            CompanionRole.DEVICE_SPECIFIC,
        ),
        CompanionToolSpec(
            CompanionToolId.NFC_LAB,
            "com.arthur.nfclab",
            ":companion:nfc-tools",
            "nfc-tools-release.apk",
            CompanionRole.OPTIONAL,
        ),
    )
}

object CompanionSuitePolicy {
    fun resolve(
        spec: CompanionToolSpec,
        observation: CompanionPackageObservation,
    ): CompanionToolState = CompanionToolState(
        spec = spec,
        availability = when {
            !observation.installed -> CompanionAvailability.MISSING
            !observation.enabled -> CompanionAvailability.DISABLED
            else -> CompanionAvailability.INSTALLED
        },
        launchable = observation.installed && observation.enabled && observation.launchable,
        versionName = observation.versionName?.takeIf { observation.installed && it.isNotBlank() },
    )
}
