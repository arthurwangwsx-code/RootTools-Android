package com.arthur.roottools.feature.network.tailscale

import android.os.Build
import com.arthur.roottools.feature.network.tailscale.data.RootTailscaleRepository
import com.arthur.roottools.feature.network.tailscale.data.RootTailscaleRuntimeInstaller
import com.arthur.roottools.feature.network.tailscale.data.RootTailscaleRuntimeSpec
import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleActionCode
import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleActionResult
import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleMode
import com.arthur.roottools.feature.network.tailscale.policy.RootTailscalePolicy
import com.arthur.roottools.privilege.PrivilegeRouter
import com.arthur.roottools.root.RootShell

class RootTailscaleController(
    private val shell: RootShell,
    private val privilegeRouter: PrivilegeRouter,
    private val repository: RootTailscaleRepository,
    private val installer: RootTailscaleRuntimeInstaller,
    private val auditSink: RootTailscaleAuditSink? = null,
) {
    private val hostname = RootTailscalePolicy.normalizeHostname("roottools-${Build.DEVICE}")

    suspend fun installOrUpdateRuntime(): RootTailscaleActionResult {
        val before = repository.read()
        if (!before.rootAvailable) return result(false, RootTailscaleActionCode.NO_ROOT, before)
        if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" }) {
            return result(false, RootTailscaleActionCode.RUNTIME_INSTALL_FAILED, before, detail = "Unsupported ABI")
        }
        val prepared = runCatching { installer.prepareVerifiedRuntime() }.getOrElse { error ->
            return auditedResult(
                success = false,
                code = RootTailscaleActionCode.RUNTIME_INSTALL_FAILED,
                before = before,
                after = repository.read(),
                action = "install_runtime",
                detail = error.message,
                rollbackHint = "Keep the previous verified Root Tailscale runtime",
            )
        }
        val shellResult = shell.execute(RootTailscaleCommands.install(prepared), timeoutSeconds = 30)
        val after = repository.read()
        val success = shellResult.success && after.runtimeInstalled && after.runtimeVersion == RootTailscaleRuntimeSpec.VERSION
        return auditedResult(
            success = success,
            code = if (success) RootTailscaleActionCode.RUNTIME_INSTALLED else RootTailscaleActionCode.RUNTIME_INSTALL_FAILED,
            before = before,
            after = after,
            action = "install_runtime",
            target = prepared.version,
            detail = shellResult.output.takeLast(1200),
            rollbackHint = "Restore /data/adb/tailscale/bin/*.previous if an update regresses",
        )
    }

    suspend fun beginAuthentication(): RootTailscaleActionResult {
        val before = repository.read()
        if (!before.rootAvailable) return result(false, RootTailscaleActionCode.NO_ROOT, before)
        if (!before.runtimeInstalled) return result(false, RootTailscaleActionCode.RUNTIME_MISSING, before)
        if (before.authenticated) {
            return result(true, RootTailscaleActionCode.AUTH_ALREADY_COMPLETE, before)
        }
        val shellResult = shell.execute(RootTailscaleCommands.beginAuthentication(hostname), timeoutSeconds = 25)
        val after = repository.read()
        val authUrl = after.authUrl ?: AUTH_URL_REGEX.find(shellResult.output)?.value
        val success = after.authenticated || authUrl != null
        return auditedResult(
            success = success,
            code = if (success) RootTailscaleActionCode.AUTH_STARTED else RootTailscaleActionCode.AUTH_REQUIRED,
            before = before,
            after = after,
            action = "begin_authentication",
            detail = shellResult.output.takeLast(1200),
            authUrl = authUrl,
            rollbackHint = "Disable Root Tailscale; authentication state is preserved until explicitly removed",
        )
    }

    suspend fun enableUserspaceServe(): RootTailscaleActionResult {
        val before = repository.read()
        if (!before.rootAvailable) return result(false, RootTailscaleActionCode.NO_ROOT, before)
        if (!before.runtimeInstalled) return result(false, RootTailscaleActionCode.RUNTIME_MISSING, before)
        if (requiresAuthentication(before)) {
            return result(false, RootTailscaleActionCode.AUTH_REQUIRED, before, authUrl = before.authUrl)
        }

        val shellResult = shell.execute(RootTailscaleCommands.enableUserspaceServe(hostname), timeoutSeconds = 45)
        val after = repository.read()
        val success = shellResult.success && after.userspaceServeReady
        return auditedResult(
            success = success,
            code = if (success) RootTailscaleActionCode.USERSPACE_SERVE_ENABLED else RootTailscaleActionCode.USERSPACE_SERVE_FAILED,
            before = before,
            after = after,
            action = "enable_userspace_serve",
            target = after.tailnetIpv4.orEmpty(),
            detail = shellResult.output.takeLast(1200),
            rollbackHint = "Disable Root Tailscale; the saved node identity is preserved",
        )
    }

    suspend fun enableRootOverlay(): RootTailscaleActionResult {
        val before = repository.read()
        if (!before.rootAvailable) return result(false, RootTailscaleActionCode.NO_ROOT, before)
        if (!before.runtimeInstalled) return result(false, RootTailscaleActionCode.RUNTIME_MISSING, before)
        if (requiresAuthentication(before)) {
            return result(false, RootTailscaleActionCode.AUTH_REQUIRED, before, authUrl = before.authUrl)
        }

        val shellResult = shell.execute(RootTailscaleCommands.enableKernel(hostname), timeoutSeconds = 40)
        val after = repository.read()
        val success = shellResult.success && after.kernelReady
        return auditedResult(
            success = success,
            code = if (success) RootTailscaleActionCode.ENABLED else RootTailscaleActionCode.ENABLE_FAILED,
            before = before,
            after = after,
            action = "enable_root_overlay",
            target = after.tailnetIpv4.orEmpty(),
            detail = shellResult.output.takeLast(1200),
            rollbackHint = "Disable Root Tailscale or reopen the official Tailscale app",
        )
    }

    suspend fun disableRootOverlay(): RootTailscaleActionResult {
        val before = repository.read()
        if (!before.rootAvailable) return result(false, RootTailscaleActionCode.NO_ROOT, before)
        val shellResult = shell.execute(RootTailscaleCommands.disable(), timeoutSeconds = 10)
        val after = repository.read()
        val success = shellResult.success && !after.daemonRunning && !after.tailscale0Present
        return auditedResult(
            success = success,
            code = if (success) RootTailscaleActionCode.DISABLED else RootTailscaleActionCode.DISABLE_FAILED,
            before = before,
            after = after,
            action = "disable_root_overlay",
            detail = shellResult.output.takeLast(1200),
            rollbackHint = "Enable Root Tailscale again; authentication state is preserved",
        )
    }

    suspend fun repair(): RootTailscaleActionResult {
        val before = repository.read()
        if (!before.rootAvailable) return result(false, RootTailscaleActionCode.NO_ROOT, before)
        if (requiresAuthentication(before)) {
            return result(false, RootTailscaleActionCode.AUTH_REQUIRED, before, authUrl = before.authUrl)
        }
        val shellResult = when (before.mode) {
            RootTailscaleMode.USERSPACE, RootTailscaleMode.USERSPACE_SERVE ->
                shell.execute(RootTailscaleCommands.enableUserspaceServe(hostname), timeoutSeconds = 45)
            else -> shell.execute(RootTailscaleCommands.enableKernel(hostname), timeoutSeconds = 45)
        }
        val after = repository.read()
        val success = shellResult.success && after.managementReady
        return auditedResult(
            success = success,
            code = if (success) RootTailscaleActionCode.REPAIRED else RootTailscaleActionCode.REPAIR_FAILED,
            before = before,
            after = after,
            action = "repair_root_overlay",
            detail = shellResult.output.takeLast(1200),
            rollbackHint = "Disable Root Tailscale if route repair causes a regression",
        )
    }

    suspend fun setBootEnabled(enabled: Boolean): RootTailscaleActionResult {
        val before = repository.read()
        if (!before.rootAvailable) return result(false, RootTailscaleActionCode.NO_ROOT, before)
        if (enabled && !RootTailscalePolicy.decide(before).canEnableBoot) {
            return result(false, RootTailscaleActionCode.BOOT_CHANGE_FAILED, before)
        }
        val command = if (enabled) RootTailscaleCommands.enableBoot(before.mode, hostname) else RootTailscaleCommands.disableBoot()
        val shellResult = shell.execute(command, timeoutSeconds = 8)
        val after = repository.read()
        val success = shellResult.success && after.bootEnabled == enabled
        return auditedResult(
            success = success,
            code = when {
                !success -> RootTailscaleActionCode.BOOT_CHANGE_FAILED
                enabled -> RootTailscaleActionCode.BOOT_ENABLED
                else -> RootTailscaleActionCode.BOOT_DISABLED
            },
            before = before,
            after = after,
            action = if (enabled) "enable_boot_restore" else "disable_boot_restore",
            detail = shellResult.output.takeLast(1200),
            rollbackHint = if (enabled) "Disable Root Tailscale boot restore" else "Enable Root Tailscale boot restore",
        )
    }

    suspend fun stopOfficialTailscaleApp(): RootTailscaleActionResult {
        val before = repository.read()
        val decision = RootTailscalePolicy.decide(before)
        if (!decision.canStopOfficialApp) {
            return result(false, RootTailscaleActionCode.OFFICIAL_APP_STOP_FAILED, before, detail = "Root overlay is not verified")
        }
        val stop = privilegeRouter.forceStop(RootTailscaleRuntimeSpec.OFFICIAL_PACKAGE)
        shell.execute("sleep 2", timeoutSeconds = 4)
        val after = repository.read()
        val success = stop.success && after.managementReady && !after.officialVpnActive
        return auditedResult(
            success = success,
            code = if (success) RootTailscaleActionCode.OFFICIAL_APP_STOPPED else RootTailscaleActionCode.OFFICIAL_APP_STOP_FAILED,
            before = before,
            after = after,
            action = "stop_official_tailscale_app",
            detail = stop.detail,
            rollbackHint = "Open the official Tailscale Android app to restore its VPN",
        )
    }

    private fun result(
        success: Boolean,
        code: RootTailscaleActionCode,
        snapshot: com.arthur.roottools.feature.network.tailscale.model.RootTailscaleSnapshot,
        authUrl: String? = null,
        detail: String? = null,
    ) = RootTailscaleActionResult(success, code, snapshot, authUrl, detail)

    private fun requiresAuthentication(
        snapshot: com.arthur.roottools.feature.network.tailscale.model.RootTailscaleSnapshot,
    ): Boolean = !snapshot.authenticated &&
        (!snapshot.hasSavedIdentity || snapshot.backendState == "NeedsLogin")

    private fun auditedResult(
        success: Boolean,
        code: RootTailscaleActionCode,
        before: com.arthur.roottools.feature.network.tailscale.model.RootTailscaleSnapshot,
        after: com.arthur.roottools.feature.network.tailscale.model.RootTailscaleSnapshot,
        action: String,
        target: String = "",
        detail: String? = null,
        authUrl: String? = null,
        rollbackHint: String,
    ): RootTailscaleActionResult {
        auditSink?.record(
            RootTailscaleAuditRecord(
            action = action,
            target = target,
            before = "mode=${before.mode};ip=${before.tailnetIpv4.orEmpty()};vpn=${before.androidVpnOwner.orEmpty()};boot=${before.bootEnabled}",
            after = "mode=${after.mode};ip=${after.tailnetIpv4.orEmpty()};vpn=${after.androidVpnOwner.orEmpty()};boot=${after.bootEnabled}",
            success = success,
            rollbackHint = rollbackHint,
            ),
        )
        return RootTailscaleActionResult(success, code, after, authUrl, detail)
    }

    private companion object {
        val AUTH_URL_REGEX = Regex("https://login\\.tailscale\\.com/a/[A-Za-z0-9_-]+")
    }
}
