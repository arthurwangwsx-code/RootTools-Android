package com.arthur.roottools.feature.companions

import android.content.Context
import android.content.pm.PackageManager

class CompanionSuiteRepository(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    fun snapshot(): List<CompanionToolState> = CompanionSuiteRegistry.tools.map { spec ->
        CompanionSuitePolicy.resolve(spec, observe(spec.packageName))
    }

    @Suppress("DEPRECATION")
    private fun observe(packageName: String): CompanionPackageObservation = runCatching {
        val application = packageManager.getApplicationInfo(
            packageName,
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        CompanionPackageObservation(
            installed = true,
            enabled = application.enabled,
            launchable = packageManager.getLaunchIntentForPackage(packageName) != null,
            versionName = packageInfo.versionName,
        )
    }.getOrElse {
        CompanionPackageObservation(installed = false)
    }
}
