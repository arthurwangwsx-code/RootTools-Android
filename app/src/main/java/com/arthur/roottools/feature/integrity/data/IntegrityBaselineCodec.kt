package com.arthur.roottools.feature.integrity.data

import com.arthur.roottools.feature.integrity.model.IntegrityBaseline
import com.arthur.roottools.feature.integrity.model.RootRuntimeProvider
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Android-free persistence codec for the trusted integrity baseline.
 *
 * Baseline comparison is safety-sensitive core logic, so its round-trip contract must execute in
 * plain JVM tests. Android's `org.json` classes are SDK stubs in local unit tests; keeping the
 * canonical codec here also prevents persistence semantics from depending on Android framework
 * behavior. The SharedPreferences adapter keeps legacy JSON decoding only as a migration path.
 */
internal object IntegrityBaselineCodec {
    private const val PREFIX = "ROOTTOOLS_INTEGRITY_BASELINE_V1"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun isEncoded(raw: String): Boolean = raw.startsWith("$PREFIX\n")

    fun encode(value: IntegrityBaseline): String {
        val fields = linkedMapOf(
            "schemaVersion" to value.schemaVersion.toString(),
            "profileName" to encodeString(value.profileName),
            "trustedAtEpochMs" to value.trustedAtEpochMs.toString(),
            "packageName" to encodeString(value.packageName),
            "versionCode" to value.versionCode.toString(),
            "signingSha256" to encodeString(value.signingSha256),
            "apkSha256" to encodeString(value.apkSha256),
            "buildFingerprint" to encodeString(value.buildFingerprint),
            "securityPatch" to encodeString(value.securityPatch),
            "verifiedBootState" to encodeString(value.verifiedBootState),
            "vbmetaDeviceState" to encodeString(value.vbmetaDeviceState),
            "flashLocked" to when (value.flashLocked) {
                true -> "true"
                false -> "false"
                null -> "null"
            },
            "selinuxMode" to encodeString(value.selinuxMode),
            "rootExpected" to value.rootExpected.toString(),
            "rootProvider" to value.rootProvider.name,
            "rootModules" to encodeSet(value.rootModules),
            "hookFrameworkPackages" to encodeSet(value.hookFrameworkPackages),
            "expectedEnvironmentPackages" to encodeSet(value.expectedEnvironmentPackages),
            "adbExpected" to value.adbExpected.toString(),
            "vpnExpected" to value.vpnExpected.toString(),
            "deviceSurfaceHashes" to encodeMap(value.deviceSurfaceHashes),
        )
        return buildString {
            append(PREFIX).append('\n')
            fields.forEach { (key, fieldValue) ->
                append(key).append('=').append(fieldValue).append('\n')
            }
        }
    }

    fun decode(raw: String): IntegrityBaseline {
        require(isEncoded(raw)) { "Unsupported integrity baseline format" }
        val fields = raw.lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .associate { line ->
                val separator = line.indexOf('=')
                require(separator > 0) { "Malformed integrity baseline field" }
                line.substring(0, separator) to line.substring(separator + 1)
            }

        return IntegrityBaseline(
            schemaVersion = fields.requireInt("schemaVersion"),
            profileName = fields.requireString("profileName"),
            trustedAtEpochMs = fields.requireLong("trustedAtEpochMs"),
            packageName = fields.requireString("packageName"),
            versionCode = fields.requireLong("versionCode"),
            signingSha256 = fields.requireString("signingSha256"),
            apkSha256 = fields.requireString("apkSha256"),
            buildFingerprint = fields.requireString("buildFingerprint"),
            securityPatch = fields.requireString("securityPatch"),
            verifiedBootState = fields.requireString("verifiedBootState"),
            vbmetaDeviceState = fields.requireString("vbmetaDeviceState"),
            flashLocked = when (fields.requireValue("flashLocked")) {
                "true" -> true
                "false" -> false
                "null" -> null
                else -> throw IllegalArgumentException("Invalid flashLocked value")
            },
            selinuxMode = fields.requireString("selinuxMode"),
            rootExpected = fields.requireBoolean("rootExpected"),
            rootProvider = runCatching {
                RootRuntimeProvider.valueOf(fields.requireValue("rootProvider"))
            }.getOrElse { throw IllegalArgumentException("Invalid rootProvider value", it) },
            rootModules = decodeSet(fields.requireValue("rootModules")),
            hookFrameworkPackages = decodeSet(fields.requireValue("hookFrameworkPackages")),
            expectedEnvironmentPackages = decodeSet(fields.requireValue("expectedEnvironmentPackages")),
            adbExpected = fields.requireBoolean("adbExpected"),
            vpnExpected = fields.requireBoolean("vpnExpected"),
            deviceSurfaceHashes = decodeMap(fields.requireValue("deviceSurfaceHashes")),
        )
    }

    private fun encodeString(value: String): String =
        encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeString(value: String): String = try {
        String(decoder.decode(value), StandardCharsets.UTF_8)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid baseline string encoding", error)
    }

    private fun encodeSet(values: Set<String>): String =
        values.sorted().joinToString(",", transform = ::encodeString)

    private fun decodeSet(raw: String): Set<String> = if (raw.isEmpty()) {
        emptySet()
    } else {
        raw.split(',').mapTo(linkedSetOf(), ::decodeString)
    }

    private fun encodeMap(values: Map<String, String>): String = values.toSortedMap().entries.joinToString(",") {
        "${encodeString(it.key)}:${encodeString(it.value)}"
    }

    private fun decodeMap(raw: String): Map<String, String> = if (raw.isEmpty()) {
        emptyMap()
    } else {
        buildMap {
            raw.split(',').forEach { entry ->
                val separator = entry.indexOf(':')
                require(separator >= 0) { "Malformed baseline map entry" }
                put(
                    decodeString(entry.substring(0, separator)),
                    decodeString(entry.substring(separator + 1)),
                )
            }
        }
    }

    private fun Map<String, String>.requireValue(key: String): String =
        this[key] ?: throw IllegalArgumentException("Missing integrity baseline field: $key")

    private fun Map<String, String>.requireString(key: String): String = decodeString(requireValue(key))

    private fun Map<String, String>.requireInt(key: String): Int =
        requireValue(key).toIntOrNull() ?: throw IllegalArgumentException("Invalid integer field: $key")

    private fun Map<String, String>.requireLong(key: String): Long =
        requireValue(key).toLongOrNull() ?: throw IllegalArgumentException("Invalid long field: $key")

    private fun Map<String, String>.requireBoolean(key: String): Boolean = when (requireValue(key)) {
        "true" -> true
        "false" -> false
        else -> throw IllegalArgumentException("Invalid boolean field: $key")
    }
}
