package com.arthur.roottools.privilege

import com.arthur.roottools.model.PrivilegeCapability
import com.arthur.roottools.model.PrivilegeRouteBackend
import com.arthur.roottools.root.RootShell

data class PrivilegeResult<T>(
    val success: Boolean,
    val value: T? = null,
    val backend: PrivilegeRouteBackend = PrivilegeRouteBackend.NONE,
    val detail: String = "",
)

class PrivilegeRouter(
    private val bridge: ShizukuBridge,
    private val shizukuClient: ShizukuUserServiceClient,
    private val rootShell: RootShell,
) {
    suspend fun forceStop(packageName: String): PrivilegeResult<Unit> {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return invalid("package name")
        return routeIdempotent(
            capability = PrivilegeCapability.ACTIVITY_CONTROL,
            shizuku = { it.forceStopPackage(pkg) },
            root = { rootAction("am force-stop --user 0 $pkg") },
        )
    }

    suspend fun setPackageEnabled(packageName: String, enabled: Boolean): PrivilegeResult<Unit> {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return invalid("package name")
        return routeIdempotent(
            capability = PrivilegeCapability.PACKAGE_CONTROL,
            shizuku = { it.setPackageEnabled(pkg, enabled) },
            root = { rootAction(if (enabled) "pm enable --user 0 $pkg" else "pm disable-user --user 0 $pkg") },
        )
    }

    suspend fun setComponentEnabled(componentName: String, enabled: Boolean): PrivilegeResult<Unit> {
        val component = PrivilegeInputValidator.componentName(componentName) ?: return invalid("component name")
        return routeIdempotent(
            capability = PrivilegeCapability.COMPONENT_CONTROL,
            shizuku = { it.setComponentEnabled(component, enabled) },
            root = { rootAction(if (enabled) "pm enable --user 0 $component" else "pm disable-user --user 0 $component") },
        )
    }

    suspend fun launchActivity(componentName: String): PrivilegeResult<Unit> {
        val component = PrivilegeInputValidator.componentName(componentName) ?: return invalid("component name")
        return routeIdempotent(
            capability = PrivilegeCapability.ACTIVITY_CONTROL,
            shizuku = { it.launchActivity(component) },
            root = { rootAction("am start --user 0 -n $component >/dev/null 2>&1") },
        )
    }

    suspend fun setStandbyBucket(packageName: String, bucket: Int): PrivilegeResult<Unit> {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return invalid("package name")
        val safeBucket = PrivilegeInputValidator.standbyBucket(bucket) ?: return invalid("standby bucket")
        return routeIdempotent(
            capability = PrivilegeCapability.PACKAGE_CONTROL,
            shizuku = { it.setStandbyBucket(pkg, safeBucket) },
            root = { rootAction("am set-standby-bucket $pkg $safeBucket") },
        )
    }

    suspend fun getStandbyBucket(packageName: String): PrivilegeResult<String> {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return invalid("package name")
        return routeRead(
            capability = PrivilegeCapability.PACKAGE_CONTROL,
            shizuku = { it.getStandbyBucket(pkg) },
            root = { rootRead("am get-standby-bucket $pkg 2>/dev/null") { it.trim() } },
        )
    }

    suspend fun setBackgroundAllowed(packageName: String, allowed: Boolean): PrivilegeResult<Unit> {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return invalid("package name")
        return routeIdempotent(
            capability = PrivilegeCapability.APP_OPS,
            shizuku = { it.setBackgroundAllowed(pkg, allowed) },
            root = {
                val mode = if (allowed) "allow" else "ignore"
                rootAction(
                "cmd appops set $pkg RUN_IN_BACKGROUND $mode; " +
                    "cmd appops set $pkg RUN_ANY_IN_BACKGROUND $mode"
                )
            },
        )
    }

    suspend fun getAppOp(packageName: String, op: String): PrivilegeResult<String> {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return invalid("package name")
        val safeOp = PrivilegeInputValidator.appOpName(op) ?: return invalid("AppOp name")
        return routeRead(
            capability = PrivilegeCapability.APP_OPS,
            shizuku = { it.getAppOp(pkg, safeOp) },
            root = { rootRead("cmd appops get $pkg $safeOp 2>/dev/null") { it.trim() } },
        )
    }

    suspend fun setAppOp(packageName: String, op: String, mode: String): PrivilegeResult<Unit> {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return invalid("package name")
        val safeOp = PrivilegeInputValidator.appOpName(op) ?: return invalid("AppOp name")
        val safeMode = PrivilegeInputValidator.appOpMode(mode) ?: return invalid("AppOp mode")
        return routeIdempotent(
            capability = PrivilegeCapability.APP_OPS,
            shizuku = { it.setAppOp(pkg, safeOp, safeMode) },
            root = { rootAction("cmd appops set $pkg $safeOp $safeMode") },
        )
    }

    suspend fun setRuntimePermission(packageName: String, permissionName: String, granted: Boolean): PrivilegeResult<Unit> {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return invalid("package name")
        val permission = PrivilegeInputValidator.permissionName(permissionName) ?: return invalid("permission name")
        return routeIdempotent(
            capability = PrivilegeCapability.PACKAGE_CONTROL,
            shizuku = { it.setRuntimePermission(pkg, permission, granted) },
            root = { rootAction("pm ${if (granted) "grant" else "revoke"} --user 0 $pkg $permission") },
        )
    }

    suspend fun setAppiumTestMode(enabled: Boolean): PrivilegeResult<Unit> = routeIdempotent(
        capability = PrivilegeCapability.PACKAGE_CONTROL,
        shizuku = { it.setAppiumTestMode(enabled) },
        root = {
            val listener = "io.appium.settings/io.appium.settings.NLService"
            rootAction(
                if (enabled) {
                    "cmd notification allow_listener $listener; dumpsys deviceidle whitelist +io.appium.settings"
                } else {
                    "cmd notification disallow_listener $listener; dumpsys deviceidle whitelist -io.appium.settings; am set-standby-bucket io.appium.settings 30"
                }
            )
        },
    )

    suspend fun isPackageRunning(packageName: String): PrivilegeResult<Boolean> {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return invalid("package name")
        return routeRead(
            capability = PrivilegeCapability.FRAMEWORK_DIAGNOSTICS,
            shizuku = { it.isPackageRunning(pkg) },
            root = { rootBooleanProbe("pidof $pkg >/dev/null 2>&1") },
        )
    }

    suspend fun getPackageEnabledState(packageName: String): PrivilegeResult<String> {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return invalid("package name")
        return routeRead(
            capability = PrivilegeCapability.PACKAGE_CONTROL,
            shizuku = { it.getPackageEnabledState(pkg) },
            root = {
                rootRead("if pm list packages -d | grep -q '^package:$pkg$'; then echo disabled-user; else echo enabled; fi") {
                    it.trim().ifBlank { "unknown" }
                }
            },
        )
    }

    suspend fun packageExists(packageName: String): PrivilegeResult<Boolean> {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return invalid("package name")
        return routeRead(
            capability = PrivilegeCapability.PACKAGE_CONTROL,
            shizuku = { it.packageExists(pkg) },
            root = { rootBooleanProbe("pm path $pkg >/dev/null 2>&1") },
        )
    }

    suspend fun topPackage(): PrivilegeResult<String> = routeRead(
        capability = PrivilegeCapability.ACTIVITY_CONTROL,
        shizuku = { it.getTopPackage() },
        root = { rootRead("dumpsys activity activities | grep -m1 topResumedActivity") { it.trim() } },
    )

    suspend fun appRuntimeSnapshot(packageName: String): PrivilegeResult<String> {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return invalid("package name")
        return routeRead(
            capability = PrivilegeCapability.ACTIVITY_CONTROL,
            shizuku = { it.appRuntimeSnapshot(pkg) },
            root = { rootRead(runtimeSnapshotCommand(pkg)) { it } },
        )
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

    private suspend fun routeIdempotent(
        capability: PrivilegeCapability,
        shizuku: (IPrivilegeUserService) -> Boolean,
        root: suspend () -> BackendAttempt<Unit>,
    ): PrivilegeResult<Unit> {
        val state = bridge.state.value
        val failures = mutableListOf<String>()
        val routes = PrivilegeRoutingPolicy.routesFor(capability, state, rootAvailable = true)
        routes.forEach { backend ->
            when (backend) {
                PrivilegeRouteBackend.SHIZUKU_ADB,
                PrivilegeRouteBackend.SHIZUKU_ROOT,
                PrivilegeRouteBackend.SUI_ROOT,
                -> {
                    val remote = shizukuClient.call(shizuku)
                    if (remote.getOrNull() == true) return PrivilegeResult(true, Unit, backend)
                    failures += "${backend.displayName}: ${remote.exceptionOrNull()?.message ?: "returned false"}"
                }
                PrivilegeRouteBackend.ROOT_SHELL -> {
                    val attempt = runCatching { root() }.getOrElse { BackendAttempt(false, detail = it.message.orEmpty()) }
                    if (attempt.success) return PrivilegeResult(true, Unit, backend, if (failures.isEmpty()) "" else failures.joinToString("; "))
                    failures += "RootShell: ${attempt.detail}"
                }
                PrivilegeRouteBackend.NONE -> Unit
            }
        }
        return PrivilegeResult(false, backend = PrivilegeRouteBackend.NONE, detail = failures.joinToString("; ").ifBlank { "No compatible privilege backend" })
    }

    private suspend fun <T> routeRead(
        capability: PrivilegeCapability,
        shizuku: (IPrivilegeUserService) -> T,
        root: suspend () -> BackendAttempt<T>,
    ): PrivilegeResult<T> {
        val state = bridge.state.value
        val failures = mutableListOf<String>()
        val routes = PrivilegeRoutingPolicy.routesFor(capability, state, rootAvailable = true)
        routes.forEach { backend ->
            when (backend) {
                PrivilegeRouteBackend.SHIZUKU_ADB,
                PrivilegeRouteBackend.SHIZUKU_ROOT,
                PrivilegeRouteBackend.SUI_ROOT,
                -> {
                    val remote = shizukuClient.call(shizuku)
                    if (remote.isSuccess) return PrivilegeResult(true, remote.getOrNull(), backend)
                    failures += "${backend.displayName}: ${remote.exceptionOrNull()?.message.orEmpty()}"
                }
                PrivilegeRouteBackend.ROOT_SHELL -> {
                    val attempt = runCatching { root() }.getOrElse { BackendAttempt(false, detail = it.message.orEmpty()) }
                    if (attempt.success) return PrivilegeResult(true, attempt.value, backend, if (failures.isEmpty()) "" else failures.joinToString("; "))
                    failures += "RootShell: ${attempt.detail}"
                }
                PrivilegeRouteBackend.NONE -> Unit
            }
        }
        return PrivilegeResult(false, backend = PrivilegeRouteBackend.NONE, detail = failures.joinToString("; ").ifBlank { "No compatible privilege backend" })
    }

    private suspend fun rootAction(command: String): BackendAttempt<Unit> {
        val result = rootShell.execute(command)
        return BackendAttempt(
            success = result.success,
            value = if (result.success) Unit else null,
            detail = rootDetail(result.exitCode, result.output, result.timedOut),
        )
    }

    private suspend fun <T> rootRead(command: String, transform: (String) -> T): BackendAttempt<T> {
        val result = rootShell.execute(command)
        return if (result.success) {
            BackendAttempt(true, transform(result.output))
        } else {
            BackendAttempt(false, detail = rootDetail(result.exitCode, result.output, result.timedOut))
        }
    }

    private suspend fun rootBooleanProbe(command: String): BackendAttempt<Boolean> {
        val result = rootShell.execute(command)
        return when (result.exitCode) {
            0 -> BackendAttempt(true, true)
            1 -> BackendAttempt(true, false)
            else -> BackendAttempt(false, detail = rootDetail(result.exitCode, result.output, result.timedOut))
        }
    }

    private fun rootDetail(exitCode: Int, output: String, timedOut: Boolean): String = when {
        timedOut -> "timeout"
        output.isNotBlank() -> "exit=$exitCode ${output.take(160)}"
        else -> "exit=$exitCode"
    }

    private fun <T> invalid(field: String): PrivilegeResult<T> = PrivilegeResult(
        success = false,
        backend = PrivilegeRouteBackend.NONE,
        detail = "Invalid $field",
    )

    private data class BackendAttempt<T>(
        val success: Boolean,
        val value: T? = null,
        val detail: String = "",
    )

}
