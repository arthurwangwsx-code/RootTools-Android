package com.arthur.roottools.privilege

import com.arthur.roottools.model.PrivilegeCapability
import com.arthur.roottools.model.PrivilegeRouteBackend
import com.arthur.roottools.model.ShadowDisplayConfig
import com.arthur.roottools.root.RootShell
import java.util.Base64

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
    suspend fun shadowDisplayStatus(): PrivilegeResult<String> = rootReadResult(
        command = shadowStatusCommand(),
        transform = { it },
    )

    suspend fun startShadowDisplay(config: ShadowDisplayConfig): PrivilegeResult<String> {
        if (config.width !in 360..2560 || config.height !in 640..3200 || config.densityDpi !in 120..640) {
            return invalid("shadow display config")
        }
        return rootReadResult(
            command = shadowStartCommand(config),
            timeoutSeconds = 8,
            transform = { it },
        )
    }

    suspend fun stopShadowDisplay(): PrivilegeResult<Unit> = rootActionResult(
        command = shadowStopCommand(),
        timeoutSeconds = 6,
    )

    suspend fun launchPackageOnShadowDisplay(packageName: String): PrivilegeResult<Unit> {
        val pkg = PrivilegeInputValidator.packageName(packageName) ?: return invalid("package name")
        return rootActionResult(shadowLaunchCommand(pkg), timeoutSeconds = 8)
    }

    suspend fun tapShadowDisplay(x: Int, y: Int): PrivilegeResult<Unit> {
        if (x !in 0..4095 || y !in 0..4095) return invalid("shadow coordinate")
        return rootActionResult(shadowInputPrefix() + " tap $x $y")
    }

    suspend fun swipeShadowDisplay(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): PrivilegeResult<Unit> {
        if (listOf(x1, y1, x2, y2).any { it !in 0..4095 } || durationMs !in 50..5_000) {
            return invalid("shadow swipe")
        }
        return rootActionResult(shadowInputPrefix() + " swipe $x1 $y1 $x2 $y2 $durationMs")
    }

    suspend fun typeTextOnShadowDisplay(text: String): PrivilegeResult<Unit> {
        if (text.length > 500 || text.any(Char::isISOControl)) return invalid("shadow text")
        val inputText = text.replace(" ", "%s")
        return rootActionResult(shadowInputPrefix() + " text ${shellQuote(inputText)}")
    }

    suspend fun pasteShadowDisplay(): PrivilegeResult<Unit> =
        rootActionResult(shadowInputPrefix() + " keyevent KEYCODE_PASTE")

    suspend fun captureShadowDisplayPreview(): PrivilegeResult<ByteArray> = rootReadResult(
        command = shadowCaptureCommand(),
        timeoutSeconds = 6,
        transform = { output ->
            val encoded = output.lineSequence()
                .dropWhile { it.trim() != SHADOW_PREVIEW_MARKER }
                .drop(1)
                .joinToString("") { it.trim() }
            if (encoded.isBlank()) error("Shadow preview unavailable")
            Base64.getDecoder().decode(encoded)
        },
    )

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

    private suspend fun rootActionResult(command: String, timeoutSeconds: Long = 8): PrivilegeResult<Unit> {
        val attempt = runCatching {
            val result = rootShell.execute(command, timeoutSeconds)
            BackendAttempt(
                success = result.success,
                value = if (result.success) Unit else null,
                detail = rootDetail(result.exitCode, result.output, result.timedOut),
            )
        }.getOrElse { BackendAttempt(false, detail = it.message.orEmpty()) }
        return PrivilegeResult(
            success = attempt.success,
            value = attempt.value,
            backend = if (attempt.success) PrivilegeRouteBackend.ROOT_SHELL else PrivilegeRouteBackend.NONE,
            detail = attempt.detail,
        )
    }

    private suspend fun <T> rootReadResult(
        command: String,
        timeoutSeconds: Long = 8,
        transform: (String) -> T,
    ): PrivilegeResult<T> {
        val result = runCatching { rootShell.execute(command, timeoutSeconds) }
            .getOrElse { return PrivilegeResult(false, backend = PrivilegeRouteBackend.NONE, detail = it.message.orEmpty()) }
        if (!result.success) {
            return PrivilegeResult(
                success = false,
                backend = PrivilegeRouteBackend.NONE,
                detail = rootDetail(result.exitCode, result.output, result.timedOut),
            )
        }
        return runCatching { transform(result.output) }.fold(
            onSuccess = { PrivilegeResult(true, it, PrivilegeRouteBackend.ROOT_SHELL) },
            onFailure = { PrivilegeResult(false, backend = PrivilegeRouteBackend.NONE, detail = it.message.orEmpty()) },
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

    private fun shadowStartCommand(config: ShadowDisplayConfig): String = """
        state_dir='$SHADOW_STATE_DIR'
        status="${'$'}state_dir/status.properties"
        ${shadowProcessMatchFunction()}
        if [ -f "${'$'}status" ]; then
          old_pid="${'$'}(sed -n 's/^pid=//p' "${'$'}status" | head -n 1)"
          case "${'$'}old_pid" in
            ''|*[!0-9]*) ;;
            *) if shadow_process_matches "${'$'}old_pid"; then echo 'shadow display already running'; exit 17; fi ;;
          esac
        fi
        rm -rf "${'$'}state_dir"
        mkdir -p "${'$'}state_dir"
        chmod 700 "${'$'}state_dir"
        apk="${'$'}(pm path $ROOTTOOLS_PACKAGE | head -n 1 | cut -d: -f2-)"
        if [ -z "${'$'}apk" ] || [ ! -f "${'$'}apk" ]; then echo 'Root Tools APK not found'; exit 18; fi
        nohup env CLASSPATH="${'$'}apk" app_process /system/bin $SHADOW_DAEMON_CLASS ${config.width} ${config.height} ${config.densityDpi} \
          >"${'$'}state_dir/daemon.log" 2>&1 </dev/null &
        echo "${'$'}!" > "${'$'}state_dir/launcher.pid"
        i=0
        while [ "${'$'}i" -lt 50 ]; do
          if [ -f "${'$'}status" ]; then
            state="${'$'}(sed -n 's/^state=//p' "${'$'}status" | head -n 1)"
            if [ "${'$'}state" = 'running' ] || [ "${'$'}state" = 'error' ]; then break; fi
          fi
          i="${'$'}((i + 1))"
          sleep 0.1
        done
        ${shadowStatusCommand()}
        state="${'$'}(sed -n 's/^state=//p' "${'$'}status" 2>/dev/null | head -n 1)"
        if [ "${'$'}state" != 'running' ]; then
          echo 'daemonLog='"${'$'}(tail -n 8 "${'$'}state_dir/daemon.log" 2>/dev/null | tr '\n' ' ' | tr '=' ':')"
          exit 19
        fi
    """.trimIndent()

    private fun shadowStopCommand(): String = """
        state_dir='$SHADOW_STATE_DIR'
        status="${'$'}state_dir/status.properties"
        ${shadowProcessMatchFunction()}
        if [ ! -f "${'$'}status" ]; then exit 0; fi
        pid="${'$'}(sed -n 's/^pid=//p' "${'$'}status" | head -n 1)"
        case "${'$'}pid" in
          ''|*[!0-9]*) exit 0 ;;
        esac
        if ! shadow_process_matches "${'$'}pid"; then
          rm -f "${'$'}state_dir/stop.request" "${'$'}state_dir/capture.request"
          exit 0
        fi
        touch "${'$'}state_dir/stop.request"
        i=0
        while shadow_process_matches "${'$'}pid" && [ "${'$'}i" -lt 30 ]; do
          i="${'$'}((i + 1))"
          sleep 0.1
        done
        if shadow_process_matches "${'$'}pid"; then kill "${'$'}pid" 2>/dev/null || true; sleep 0.3; fi
        if shadow_process_matches "${'$'}pid"; then kill -9 "${'$'}pid" 2>/dev/null || true; fi
        rm -f "${'$'}state_dir/stop.request" "${'$'}state_dir/capture.request"
    """.trimIndent()

    private fun shadowStatusCommand(): String = """
        state_dir='$SHADOW_STATE_DIR'
        status="${'$'}state_dir/status.properties"
        ${shadowProcessMatchFunction()}
        if [ -f "${'$'}status" ]; then cat "${'$'}status"; else echo 'state=stopped'; fi
        pid="${'$'}(sed -n 's/^pid=//p' "${'$'}status" 2>/dev/null | head -n 1)"
        case "${'$'}pid" in
          ''|*[!0-9]*) echo 'processAlive=0' ;;
          *) if shadow_process_matches "${'$'}pid"; then echo 'processAlive=1'; else echo 'processAlive=0'; fi ;;
        esac
        printf 'activeDisplays='
        ids="${'$'}(cmd display get-displays 2>/dev/null | sed -n 's/.*Display id \([0-9][0-9]*\):.*/\1/p' | tr '\n' ',' | sed 's/,$//')"
        if [ -z "${'$'}ids" ]; then
          ids="${'$'}(cmd display get-displays -i 2>/dev/null | sed -n 's/[^0-9]*\([0-9][0-9]*\).*/\1/p' | tr '\n' ',' | sed 's/,$//')"
        fi
        printf '%s' "${'$'}ids"
        printf '\n'
    """.trimIndent()

    private fun shadowLaunchCommand(pkg: String): String = """
        ${shadowRequireDisplayCommand()}
        component="${'$'}(cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $pkg 2>/dev/null | tail -n 1)"
        case "${'$'}component" in
          */*) ;;
          *) echo 'No launcher activity for $pkg'; exit 22 ;;
        esac
        am start --user 0 --display "${'$'}display_id" -n "${'$'}component" >/dev/null
    """.trimIndent()

    private fun shadowInputPrefix(): String = shadowRequireDisplayCommand() + "\ninput -d \"${'$'}display_id\""

    private fun shadowRequireDisplayCommand(): String = """
        status='$SHADOW_STATE_DIR/status.properties'
        ${shadowProcessMatchFunction()}
        if [ ! -f "${'$'}status" ]; then echo 'Shadow display not started'; exit 20; fi
        display_id="${'$'}(sed -n 's/^displayId=//p' "${'$'}status" | head -n 1)"
        pid="${'$'}(sed -n 's/^pid=//p' "${'$'}status" | head -n 1)"
        case "${'$'}display_id" in ''|*[!0-9]*) echo 'Invalid shadow display id'; exit 20 ;; esac
        case "${'$'}pid" in ''|*[!0-9]*) echo 'Invalid shadow daemon pid'; exit 20 ;; esac
        if ! shadow_process_matches "${'$'}pid"; then echo 'Shadow daemon is not alive'; exit 21; fi
    """.trimIndent()

    private fun shadowProcessMatchFunction(): String = """
        shadow_process_matches() {
          case "${'$'}1" in ''|*[!0-9]*) return 1 ;; esac
          [ -r "/proc/${'$'}1/cmdline" ] || return 1
          tr '\000' ' ' < "/proc/${'$'}1/cmdline" 2>/dev/null | grep -Fq '$SHADOW_DAEMON_CLASS'
        }
    """.trimIndent()

    private fun shadowCaptureCommand(): String = """
        ${shadowRequireDisplayCommand()}
        state_dir='$SHADOW_STATE_DIR'
        rm -f "${'$'}state_dir/preview.jpg"
        touch "${'$'}state_dir/capture.request"
        i=0
        while [ "${'$'}i" -lt 40 ] && [ ! -s "${'$'}state_dir/preview.jpg" ]; do
          i="${'$'}((i + 1))"
          sleep 0.1
        done
        if [ ! -s "${'$'}state_dir/preview.jpg" ]; then echo 'Preview timed out'; exit 24; fi
        echo '$SHADOW_PREVIEW_MARKER'
        base64 "${'$'}state_dir/preview.jpg"
    """.trimIndent()

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

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

    private companion object {
        const val ROOTTOOLS_PACKAGE = "com.arthur.roottools"
        const val SHADOW_DAEMON_CLASS = "com.arthur.roottools.privilege.shadow.ShadowDisplayDaemon"
        const val SHADOW_STATE_DIR = "/data/local/tmp/roottools-shadow"
        const val SHADOW_PREVIEW_MARKER = "__ROOTTOOLS_SHADOW_PREVIEW__"
    }

}
