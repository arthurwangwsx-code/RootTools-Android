package com.arthur.roottools.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.arthur.roottools.integration.termux.TermuxCapabilityInput
import com.arthur.roottools.integration.termux.TermuxCapabilityPolicy
import com.arthur.roottools.model.TermuxRuntimeSnapshot

class TermuxRuntimeRepository(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    fun read(): TermuxRuntimeSnapshot {
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(TERMUX_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            }
        }.getOrNull()

        if (packageInfo == null) {
            return TermuxRuntimeSnapshot(checkedAtEpochMs = System.currentTimeMillis())
        }

        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(TERMUX_PACKAGE).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(TERMUX_PACKAGE)
            }
        }.getOrNull()

        val runCommandServiceAvailable = queryRunCommandService()
        val permissionAvailable = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPermissionInfo(RUN_COMMAND_PERMISSION, 0)
        }.isSuccess
        val permissionGranted = permissionAvailable &&
            appContext.checkSelfPermission(RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val decision = TermuxCapabilityPolicy.decide(
            TermuxCapabilityInput(
                installed = true,
                versionName = packageInfo.versionName,
                installerPackageName = installer,
                runCommandServiceAvailable = runCommandServiceAvailable,
                runCommandPermissionAvailable = permissionAvailable,
                runCommandPermissionGranted = permissionGranted,
            )
        )

        return TermuxRuntimeSnapshot(
            installed = true,
            versionName = packageInfo.versionName,
            versionCode = versionCode,
            installerPackageName = installer,
            distribution = decision.distribution,
            runCommandServiceAvailable = runCommandServiceAvailable,
            runCommandPermissionAvailable = permissionAvailable,
            runCommandPermissionGranted = permissionGranted,
            bridgeMode = decision.bridgeMode,
            checkedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private fun queryRunCommandService(): Boolean {
        val intent = Intent(RUN_COMMAND_ACTION).setPackage(TERMUX_PACKAGE)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentServices(intent, 0)
            }
        }.getOrDefault(emptyList()).any { resolveInfo ->
            resolveInfo.serviceInfo?.packageName == TERMUX_PACKAGE
        }
    }

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
        const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
    }
}

