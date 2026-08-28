package com.arthur.roottools.privilege

import android.os.Process
import android.os.Parcel
import java.util.concurrent.TimeUnit

/**
 * Shizuku/Sui UserService. This is instantiated in a remote app_process with shell/root UID.
 * Only fixed semantic methods are exposed; there is deliberately no arbitrary command API.
 */
class PrivilegeUserService : IPrivilegeUserService.Stub() {
    override fun getBackendUid(): Int = Process.myUid()

    override fun packageExists(packageName: String): Boolean {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return false
        return run("pm path $pkg >/dev/null 2>&1").success
    }

    override fun getPackageEnabledState(packageName: String): String {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return "unknown"
        return if (run("pm list packages -d | grep -q '^package:$pkg$'").success) "disabled-user" else "enabled"
    }

    override fun isPackageRunning(packageName: String): Boolean {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return false
        return run("pidof $pkg >/dev/null 2>&1").success
    }

    override fun forceStopPackage(packageName: String): Boolean {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return false
        return run("am force-stop --user 0 $pkg").success
    }

    override fun setPackageEnabled(packageName: String, enabled: Boolean): Boolean {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return false
        return run(if (enabled) "pm enable --user 0 $pkg" else "pm disable-user --user 0 $pkg").success
    }

    override fun setComponentEnabled(componentName: String, enabled: Boolean): Boolean {
        val component = PrivilegeInputValidator.componentName(componentName) ?: return false
        return run(if (enabled) "pm enable --user 0 $component" else "pm disable-user --user 0 $component").success
    }

    override fun launchActivity(componentName: String): Boolean {
        val component = PrivilegeInputValidator.componentName(componentName) ?: return false
        return run("am start --user 0 -n $component >/dev/null 2>&1").success
    }

    override fun getStandbyBucket(packageName: String): String {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return ""
        return run("am get-standby-bucket $pkg 2>/dev/null").output.trim()
    }

    override fun setStandbyBucket(packageName: String, bucket: Int): Boolean {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return false
        val safeBucket = PrivilegeInputValidator.standbyBucket(bucket) ?: return false
        return run("am set-standby-bucket $pkg $safeBucket").success
    }

    override fun getAppOp(packageName: String, opName: String): String {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return ""
        val op = PrivilegeInputValidator.appOpName(opName) ?: return ""
        return run("cmd appops get $pkg $op 2>/dev/null").output.trim()
    }

    override fun setAppOp(packageName: String, opName: String, mode: String): Boolean {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return false
        val op = PrivilegeInputValidator.appOpName(opName) ?: return false
        val safeMode = PrivilegeInputValidator.appOpMode(mode) ?: return false
        return run("cmd appops set $pkg $op $safeMode").success
    }

    override fun setRuntimePermission(packageName: String, permissionName: String, granted: Boolean): Boolean {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return false
        val permission = PrivilegeInputValidator.permissionName(permissionName) ?: return false
        return run("pm ${if (granted) "grant" else "revoke"} --user 0 $pkg $permission").success
    }

    override fun setBackgroundAllowed(packageName: String, allowed: Boolean): Boolean {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return false
        val mode = if (allowed) "allow" else "ignore"
        return run(
            "cmd appops set $pkg RUN_IN_BACKGROUND $mode && " +
                "cmd appops set $pkg RUN_ANY_IN_BACKGROUND $mode"
        ).success
    }

    override fun setAppiumTestMode(enabled: Boolean): Boolean {
        val listener = "io.appium.settings/io.appium.settings.NLService"
        val command = if (enabled) {
            "pm enable io.appium.settings >/dev/null 2>&1; cmd notification allow_listener $listener && dumpsys deviceidle whitelist +io.appium.settings"
        } else {
            "cmd notification disallow_listener $listener; dumpsys deviceidle whitelist -io.appium.settings; am set-standby-bucket io.appium.settings 30"
        }
        return run(command).success
    }

    override fun getAssistantRoleHolder(): String = run(
        "cmd role get-role-holders --user 0 android.app.role.ASSISTANT 2>/dev/null"
    ).output.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty).orEmpty()

    override fun setAssistantRoleHolder(packageName: String): Boolean {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return false
        return run(
            "cmd role add-role-holder --user 0 android.app.role.ASSISTANT $pkg 0"
        ).success
    }

    override fun getTopPackage(): String = run(
        """dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity' | sed -n 's/.* u[0-9]* \([^/ ]*\)\/.*/\1/p'"""
    ).output.trim()

    override fun appRuntimeSnapshot(packageName: String): String {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return ""
        return run(runtimeSnapshotCommand(pkg), timeoutSeconds = 10).output
    }

    override fun frameworkSelfTest(ownPackageName: String): String {
        val pkg = PrivilegeInputValidator.packageName(ownPackageName) ?: return "invalid-package"
        val pm = if (packageExists(pkg)) "pm=ok" else "pm=fail"
        val activity = if (run("dumpsys activity activities >/dev/null 2>&1").success) "activity=ok" else "activity=fail"
        val appops = if (run("cmd appops get $pkg RUN_IN_BACKGROUND >/dev/null 2>&1").success) "appops=ok" else "appops=fail"
        return "uid=${Process.myUid()};$pm;$activity;$appops"
    }

    private fun runtimeSnapshotCommand(pkg: String): String = """
        echo '__PROCESSES__'
        ps -A -o PID,PPID,USER,%CPU,%MEM,RES,ELAPSED,ARGS 2>/dev/null | awk -v p='$pkg' '${'$'}8==p || index(${'$'}8,p ":")==1 {printf "%s|%s|%s|%s|%s|%s|%s|%s\n",${'$'}1,${'$'}2,${'$'}3,${'$'}4,${'$'}5,${'$'}6,${'$'}7,${'$'}8}'
        echo '__SERVICES__'
        dumpsys activity services $pkg 2>/dev/null | grep -E 'ServiceRecord\{|isForeground=true|processName=' | head -n 160
        echo '__BUCKET__'
        am get-standby-bucket $pkg 2>/dev/null
        echo '__DOZE__'
        dumpsys deviceidle whitelist 2>/dev/null | grep -F "$pkg" | head -n 20
        echo '__WAKELOCK__'
        dumpsys power 2>/dev/null | grep -Fi "$pkg" | head -n 40
    """.trimIndent()

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

    private fun run(command: String, timeoutSeconds: Long = 8): CommandResult = try {
        val process = ProcessBuilder("/system/bin/sh", "-c", command).redirectErrorStream(true).start()
        val builder = StringBuilder()
        val readerThread = Thread({
            runCatching {
                process.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(2048)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count <= 0) break
                        if (builder.length < MAX_OUTPUT) {
                            builder.append(buffer, 0, minOf(count, MAX_OUTPUT - builder.length))
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
            readerThread.join(500)
            CommandResult(-1, "timeout")
        } else {
            readerThread.join(1_000)
            CommandResult(process.exitValue(), builder.toString())
        }
    } catch (error: Throwable) {
        CommandResult(-1, error.message.orEmpty())
    }

    private companion object {
        const val MAX_OUTPUT = 32_000
        const val SHIZUKU_USER_SERVICE_DESTROY_TRANSACTION = 16_777_115
    }
}
