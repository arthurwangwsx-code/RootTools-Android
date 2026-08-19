package com.arthur.roottools.privilege

import android.os.Parcel
import android.os.Process
import java.util.concurrent.TimeUnit

/**
 * Shizuku/Sui UserService running under the server identity (shell or root).
 *
 * This API intentionally exposes only semantic operations. There is no generic `exec(command)`
 * Binder method, so UI/automation cannot turn this service into an unrestricted privileged shell.
 */
class PrivilegeUserService : IPrivilegeUserService.Stub() {
    override fun getBackendUid(): Int = Process.myUid()

    override fun packageExists(packageName: String): Boolean {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return false
        return runCommand("pm path $pkg >/dev/null 2>&1").success
    }

    override fun getPackageEnabledState(packageName: String): String {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return "unknown"
        return if (runCommand("pm list packages -d | grep -q '^package:$pkg$'").success) "disabled-user" else "enabled"
    }

    override fun isPackageRunning(packageName: String): Boolean {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return false
        return runCommand("pidof $pkg >/dev/null 2>&1").success
    }

    override fun forceStopPackage(packageName: String): Boolean {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return false
        return runCommand("am force-stop --user 0 $pkg").success
    }

    override fun setPackageEnabled(packageName: String, enabled: Boolean): Boolean {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return false
        val command = if (enabled) "pm enable --user 0 $pkg" else "pm disable-user --user 0 $pkg"
        return runCommand(command).success
    }

    override fun getStandbyBucket(packageName: String): String {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return ""
        return runCommand("am get-standby-bucket $pkg 2>/dev/null").output.trim()
    }

    override fun setStandbyBucket(packageName: String, bucket: Int): Boolean {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return false
        val safeBucket = PrivilegeInputValidator.standbyBucket(bucket) ?: return false
        return runCommand("am set-standby-bucket $pkg $safeBucket").success
    }

    override fun getAppOp(packageName: String, opName: String): String {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return ""
        val op = PrivilegeInputValidator.appOpName(opName) ?: return ""
        return runCommand("cmd appops get $pkg $op 2>/dev/null").output.trim()
    }

    override fun setAppOp(packageName: String, opName: String, mode: String): Boolean {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return false
        val op = PrivilegeInputValidator.appOpName(opName) ?: return false
        val safeMode = PrivilegeInputValidator.appOpMode(mode) ?: return false
        return runCommand("cmd appops set $pkg $op $safeMode").success
    }

    override fun setBackgroundAllowed(packageName: String, allowed: Boolean): Boolean {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return false
        val mode = if (allowed) "allow" else "ignore"
        return runCommand(
            "cmd appops set $pkg RUN_IN_BACKGROUND $mode && " +
                "cmd appops set $pkg RUN_ANY_IN_BACKGROUND $mode"
        ).success
    }

    override fun setComponentEnabled(componentName: String, enabled: Boolean): Boolean {
        val component = PrivilegeInputValidator.componentName(componentName) ?: return false
        val command = if (enabled) "pm enable --user 0 $component" else "pm disable-user --user 0 $component"
        return runCommand(command).success
    }

    override fun getTopPackage(): String = runCommand(
        """dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity' | sed -n 's/.* u[0-9]* \([^/ ]*\)\/.*/\1/p'"""
    ).output.trim()

    override fun setAppiumTestMode(enabled: Boolean): Boolean {
        val listener = "io.appium.settings/io.appium.settings.NLService"
        val command = if (enabled) {
            "pm enable --user 0 io.appium.settings >/dev/null 2>&1; " +
                "cmd notification allow_listener $listener && dumpsys deviceidle whitelist +io.appium.settings"
        } else {
            "cmd notification disallow_listener $listener; " +
                "dumpsys deviceidle whitelist -io.appium.settings; " +
                "am set-standby-bucket io.appium.settings 30"
        }
        return runCommand(command).success
    }

    override fun frameworkSelfTest(ownPackageName: String): String {
        val pkg = PrivilegeInputValidator.packageName(ownPackageName) ?: return "invalid-package"
        val pm = if (packageExists(pkg)) "pm=ok" else "pm=fail"
        val activity = if (runCommand("dumpsys activity activities >/dev/null 2>&1").success) "activity=ok" else "activity=fail"
        // Query the package as a whole. A specific op may legitimately be unset, which should not
        // make the read-only capability probe look broken.
        val appops = if (runCommand("cmd appops get $pkg >/dev/null 2>&1").success) "appops=ok" else "appops=fail"
        return "uid=${Process.myUid()};$pm;$activity;$appops"
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == SHIZUKU_USER_SERVICE_DESTROY_TRANSACTION) {
            System.exit(0)
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    private data class CommandResult(val exitCode: Int, val output: String) {
        val success: Boolean get() = exitCode == 0
    }

    private fun runCommand(command: String, timeoutSeconds: Long = 8): CommandResult = try {
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = Thread({
            runCatching {
                process.inputStream.bufferedReader().use { input ->
                    val buffer = CharArray(2048)
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        if (output.length < MAX_OUTPUT) {
                            output.append(buffer, 0, minOf(count, MAX_OUTPUT - output.length))
                        }
                    }
                }
            }
        }, "RootTools-shizuku-reader").apply {
            isDaemon = true
            start()
        }
        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            reader.join(500)
            CommandResult(-1, "timeout")
        } else {
            reader.join(1_000)
            CommandResult(process.exitValue(), output.toString())
        }
    } catch (error: Throwable) {
        CommandResult(-1, error.message.orEmpty())
    }

    private companion object {
        const val MAX_OUTPUT = 32_000
        // Shizuku UserService convention for an explicit destroy transaction.
        const val SHIZUKU_USER_SERVICE_DESTROY_TRANSACTION = 16_777_115
    }
}
