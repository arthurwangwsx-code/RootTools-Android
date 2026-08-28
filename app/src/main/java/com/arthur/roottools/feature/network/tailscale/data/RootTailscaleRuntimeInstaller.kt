package com.arthur.roottools.feature.network.tailscale.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class RootTailscaleRuntimeInstaller(context: Context) {
    data class RuntimePayload(
        val version: String,
        val tailscale: File,
        val tailscaled: File,
        val packageSha256: String,
    )

    private val appContext = context.applicationContext

    suspend fun prepareVerifiedRuntime(): RuntimePayload = withContext(Dispatchers.IO) {
        val root = File(appContext.cacheDir, "root-tailscale/${RootTailscaleRuntimeSpec.VERSION}").apply { mkdirs() }
        val archive = File(root, "tailscale_${RootTailscaleRuntimeSpec.VERSION}_${RootTailscaleRuntimeSpec.ARCH}.tgz")
        if (!archive.isFile || sha256(archive) != RootTailscaleRuntimeSpec.SHA256) {
            val partial = File(root, "${archive.name}.part")
            partial.delete()
            download(URL(RootTailscaleRuntimeSpec.DOWNLOAD_URL), partial)
            val actual = sha256(partial)
            check(actual == RootTailscaleRuntimeSpec.SHA256) {
                "Tailscale package hash mismatch: expected ${RootTailscaleRuntimeSpec.SHA256}, got $actual"
            }
            if (!partial.renameTo(archive)) {
                partial.copyTo(archive, overwrite = true)
                partial.delete()
            }
        }
        val actual = sha256(archive)
        check(actual == RootTailscaleRuntimeSpec.SHA256) { "Cached Tailscale package failed hash verification" }
        val extracted = VerifiedTarGzExtractor.extractRuntime(archive, File(root, "runtime"))
        RuntimePayload(
            version = RootTailscaleRuntimeSpec.VERSION,
            tailscale = extracted.tailscale,
            tailscaled = extracted.tailscaled,
            packageSha256 = actual,
        )
    }

    private fun download(url: URL, target: File) {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "RootTools/${RootTailscaleRuntimeSpec.VERSION} root-tailscale-installer")
        }
        try {
            val code = connection.responseCode
            check(code in 200..299) { "Tailscale download failed with HTTP $code" }
            target.parentFile?.mkdirs()
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output, 128 * 1024) }
            }
        } finally {
            connection.disconnect()
        }
    }

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 120_000
    }
}

