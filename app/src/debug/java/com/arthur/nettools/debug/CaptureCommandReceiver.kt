package com.arthur.nettools.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arthur.nettools.capture.AppTarget
import com.arthur.nettools.capture.CaptureRepository
import com.arthur.nettools.intercept.InterceptionOptions
import com.arthur.nettools.intercept.InterceptionService
import com.arthur.nettools.intercept.MitmAddonClient
import com.arthur.nettools.intercept.MitmAddonManager
import com.arthur.nettools.security.CertificateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Debug-build-only automation bridge used by scripts/validate-device.sh. */
class CaptureCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = CaptureRepository(context.applicationContext)
                repo.initialize()
                when (intent.getStringExtra("command")) {
                    "start" -> {
                        val packageName = intent.getStringExtra("package")
                        val target = packageName?.let { pkg ->
                            repo.installedApps().firstOrNull { it.packageName == pkg }
                                ?: runCatching {
                                    val info = context.packageManager.getApplicationInfo(pkg, 0)
                                    AppTarget(context.packageManager.getApplicationLabel(info).toString(), pkg, info.uid)
                                }.getOrNull()
                        }
                        repo.start(target)
                    }
                    "stop" -> repo.stop()
                    "recover" -> repo.initialize()
                    "install_addon" -> MitmAddonManager(context.applicationContext).installLatest()
                    "import_ca" -> {
                        val client = MitmAddonClient(context.applicationContext)
                        val pem = client.requestCertificate()
                        CertificateManager(context.applicationContext).importPem(pem, "PCAPdroid MITM / mitmproxy")
                        client.disconnect()
                    }
                    "install_ca" -> CertificateManager(context.applicationContext).installSystemModule().getOrThrow()
                    "start_intercept" -> {
                        val packageName = intent.getStringExtra("package") ?: return@launch
                        val info = context.packageManager.getApplicationInfo(packageName, 0)
                        val label = context.packageManager.getApplicationLabel(info).toString()
                        context.startForegroundService(Intent(context, InterceptionService::class.java).apply {
                            action = InterceptionService.ACTION_START
                            putExtra(InterceptionService.EXTRA_LABEL, label)
                            putExtra(InterceptionService.EXTRA_PACKAGE, packageName)
                            putExtra(InterceptionService.EXTRA_UID, info.uid)
                            putExtra(InterceptionService.EXTRA_BLOCK_QUIC, true)
                            putExtra(InterceptionService.EXTRA_RESTART, true)
                            putExtra(InterceptionService.EXTRA_FULL_PAYLOAD, true)
                        })
                    }
                    "stop_intercept" -> context.startService(Intent(context, InterceptionService::class.java).apply { action = InterceptionService.ACTION_STOP })
                }
            } finally {
                pending.finish()
            }
        }
    }
}
