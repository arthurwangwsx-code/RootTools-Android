package com.arthur.nettools.intercept

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.arthur.nettools.capture.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MitmAddonManager(private val context: Context) {
    companion object {
        const val PACKAGE = "com.pcapdroid.mitm"
        private const val LATEST_API = "https://api.github.com/repos/emanuele-f/PCAPdroid-mitm/releases/latest"
    }

    fun installedStatus(): AddonStatus {
        val pm = context.packageManager
        val info = runCatching { pm.getPackageInfo(PACKAGE, 0) }.getOrNull()
        return AddonStatus(
            installed = info != null,
            versionName = info?.versionName,
            versionCode = if (info != null) info.longVersionCode else -1,
            supportedAbi = Build.SUPPORTED_ABIS.firstOrNull(),
        )
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        val manager = context.getSystemService(PowerManager::class.java) ?: return false
        return manager.isIgnoringBatteryOptimizations(PACKAGE)
    }

    suspend fun queryLatest(): AddonStatus = withContext(Dispatchers.IO) {
        val installed = installedStatus()
        runCatching {
            val conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Net-Tools-Android")
            }
            val json = conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val tag = json.optString("tag_name").removePrefix("v")
            val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            val assets = json.optJSONArray("assets")
            var url: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.optString("name")
                    if (name.endsWith("_${abi}.apk")) {
                        url = a.optString("browser_download_url")
                        break
                    }
                }
            }
            installed.copy(latestVersion = tag, latestAssetUrl = url)
        }.getOrElse { installed.copy(message = "Latest-version check failed: ${it.message}") }
    }

    suspend fun installLatest(onProgress: (String) -> Unit = {}): Result<AddonStatus> = withContext(Dispatchers.IO) {
        runCatching {
            check(RootShell.hasRoot()) { "Root is required to install the add-on silently" }
            val latest = queryLatest()
            val assetUrl = latest.latestAssetUrl ?: error("No compatible add-on APK for ${latest.supportedAbi}")
            onProgress("Downloading MITM add-on ${latest.latestVersion}")
            val outDir = File(context.getExternalFilesDir(null), "addons").apply { mkdirs() }
            val apk = File(outDir, "pcapdroid-mitm-${latest.latestVersion}-${latest.supportedAbi}.apk")
            val conn = (URL(assetUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "Net-Tools-Android")
            }
            conn.inputStream.use { input -> apk.outputStream().use { input.copyTo(it) } }
            check(apk.length() > 1_000_000) { "Downloaded add-on APK is unexpectedly small" }
            onProgress("Installing MITM add-on")
            val tmp = "/data/local/tmp/nettools-mitm.apk"
            val command = "cp '${apk.absolutePath}' '$tmp' && chmod 0644 '$tmp' && pm install -r '$tmp'; rv=\$?; rm -f '$tmp'; exit \$rv"
            val result = RootShell.exec(command)
            check(result.code == 0 && result.output.contains("Success", true)) { result.output.ifBlank { "Package installation failed" } }
            installedStatus().copy(latestVersion = latest.latestVersion, latestAssetUrl = assetUrl, message = "MITM add-on installed")
        }
    }
}
