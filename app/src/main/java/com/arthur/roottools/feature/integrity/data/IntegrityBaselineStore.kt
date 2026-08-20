package com.arthur.roottools.feature.integrity.data

import android.content.Context
import com.arthur.roottools.feature.integrity.model.IntegrityBaseline
import com.arthur.roottools.feature.integrity.model.RootRuntimeProvider
import org.json.JSONArray
import org.json.JSONObject

class IntegrityBaselineStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): IntegrityBaseline? = preferences.getString(KEY_BASELINE, null)
        ?.let { runCatching { decode(it) }.getOrNull() }

    fun save(baseline: IntegrityBaseline) {
        preferences.edit().putString(KEY_BASELINE, encode(baseline)).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_BASELINE).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "environment_integrity_baseline"
        private const val KEY_BASELINE = "trusted_baseline"

        internal fun encode(value: IntegrityBaseline): String = IntegrityBaselineCodec.encode(value)

        internal fun decode(raw: String): IntegrityBaseline = if (IntegrityBaselineCodec.isEncoded(raw)) {
            IntegrityBaselineCodec.decode(raw)
        } else {
            decodeLegacyJson(raw)
        }

        /** Read compatibility for baselines written by the initial 0.3.0 development builds. */
        private fun decodeLegacyJson(raw: String): IntegrityBaseline {
            val root = JSONObject(raw)
            return IntegrityBaseline(
                schemaVersion = root.optInt("schemaVersion", 1),
                profileName = root.getString("profileName"),
                trustedAtEpochMs = root.getLong("trustedAtEpochMs"),
                packageName = root.optString("packageName"),
                versionCode = root.optLong("versionCode"),
                signingSha256 = root.optString("signingSha256"),
                apkSha256 = root.optString("apkSha256"),
                buildFingerprint = root.optString("buildFingerprint"),
                securityPatch = root.optString("securityPatch"),
                verifiedBootState = root.optString("verifiedBootState"),
                vbmetaDeviceState = root.optString("vbmetaDeviceState"),
                flashLocked = when {
                    !root.has("flashLocked") || root.isNull("flashLocked") -> null
                    else -> root.getBoolean("flashLocked")
                },
                selinuxMode = root.optString("selinuxMode"),
                rootExpected = root.optBoolean("rootExpected"),
                rootProvider = runCatching { RootRuntimeProvider.valueOf(root.optString("rootProvider")) }
                    .getOrDefault(RootRuntimeProvider.UNKNOWN),
                rootModules = root.optJSONArray("rootModules").toStringSet(),
                hookFrameworkPackages = root.optJSONArray("hookFrameworkPackages").toStringSet(),
                expectedEnvironmentPackages = root.optJSONArray("expectedEnvironmentPackages").toStringSet(),
                adbExpected = root.optBoolean("adbExpected"),
                vpnExpected = root.optBoolean("vpnExpected"),
                deviceSurfaceHashes = root.optJSONObject("deviceSurfaceHashes").toStringMap(),
            )
        }

        @Suppress("unused")
        private fun encodeLegacyJson(value: IntegrityBaseline): String = JSONObject().apply {
            put("schemaVersion", value.schemaVersion)
            put("profileName", value.profileName)
            put("trustedAtEpochMs", value.trustedAtEpochMs)
            put("packageName", value.packageName)
            put("versionCode", value.versionCode)
            put("signingSha256", value.signingSha256)
            put("apkSha256", value.apkSha256)
            put("buildFingerprint", value.buildFingerprint)
            put("securityPatch", value.securityPatch)
            put("verifiedBootState", value.verifiedBootState)
            put("vbmetaDeviceState", value.vbmetaDeviceState)
            if (value.flashLocked == null) put("flashLocked", JSONObject.NULL) else put("flashLocked", value.flashLocked)
            put("selinuxMode", value.selinuxMode)
            put("rootExpected", value.rootExpected)
            put("rootProvider", value.rootProvider.name)
            put("rootModules", JSONArray(value.rootModules.sorted()))
            put("hookFrameworkPackages", JSONArray(value.hookFrameworkPackages.sorted()))
            put("expectedEnvironmentPackages", JSONArray(value.expectedEnvironmentPackages.sorted()))
            put("adbExpected", value.adbExpected)
            put("vpnExpected", value.vpnExpected)
            put("deviceSurfaceHashes", JSONObject(value.deviceSurfaceHashes.toSortedMap()))
        }.toString()

        private fun JSONArray?.toStringSet(): Set<String> = buildSet {
            val array = this@toStringSet ?: return@buildSet
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }

        private fun JSONObject?.toStringMap(): Map<String, String> = buildMap {
            val objectValue = this@toStringMap ?: return@buildMap
            val keys = objectValue.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                objectValue.optString(key).takeIf { it.isNotBlank() }?.let { put(key, it) }
            }
        }
    }
}
