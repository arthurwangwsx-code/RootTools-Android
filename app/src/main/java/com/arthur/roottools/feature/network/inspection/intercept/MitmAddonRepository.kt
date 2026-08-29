package com.arthur.roottools.feature.network.inspection.intercept

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.arthur.roottools.feature.network.inspection.intercept.AddonStatus

class MitmAddonRepository(private val context: Context) {
    fun installedStatus(): AddonStatus {
        val info = runCatching {
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
        }.getOrNull() ?: return AddonStatus(installed = false, supportedAbi = supportedAbi())
        return AddonStatus(
            installed = true,
            versionName = info.versionName,
            versionCode = info.longVersionCode,
            supportedAbi = supportedAbi(),
        )
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        val manager = context.getSystemService(PowerManager::class.java) ?: return false
        return manager.isIgnoringBatteryOptimizations(PACKAGE_NAME)
    }

    private fun supportedAbi(): String? = Build.SUPPORTED_ABIS.firstOrNull {
        it == "arm64-v8a" || it == "x86_64"
    }

    companion object {
        const val PACKAGE_NAME = "com.pcapdroid.mitm"
    }
}
