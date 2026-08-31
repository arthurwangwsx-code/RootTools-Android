package com.arthur.roottools.feature.network.tailscale.model

enum class RootTailscaleMode {
    NOT_INSTALLED,
    STOPPED,
    USERSPACE,
    USERSPACE_SERVE,
    KERNEL_TUN,
}

enum class RootTailscaleHealth {
    RUNTIME_MISSING,
    STOPPED,
    NEEDS_LOGIN,
    READY,
    DEGRADED,
}

data class RootTailscaleSnapshot(
    val rootAvailable: Boolean = false,
    val runtimeInstalled: Boolean = false,
    val runtimeVersion: String? = null,
    val daemonRunning: Boolean = false,
    val socketReady: Boolean = false,
    val statePresent: Boolean = false,
    val identitySaved: Boolean = false,
    val tailscale0Present: Boolean = false,
    val tailnetIpv4: String? = null,
    val authUrl: String? = null,
    val backendState: String? = null,
    val backendOnline: Boolean = false,
    val routeReady: Boolean = false,
    val serveAdbReady: Boolean = false,
    val serveMcpReady: Boolean = false,
    val bootEnabled: Boolean = false,
    val adb5555Listening: Boolean = false,
    val mcp8765Listening: Boolean = false,
    val androidVpnActive: Boolean = false,
    val androidVpnOwner: String? = null,
    val officialAppInstalled: Boolean = false,
    val hiddifyInstalled: Boolean = false,
    val collectedAtMs: Long = 0L,
) {
    val mode: RootTailscaleMode
        get() = when {
            !runtimeInstalled -> RootTailscaleMode.NOT_INSTALLED
            tailscale0Present && daemonRunning -> RootTailscaleMode.KERNEL_TUN
            serveAdbReady && daemonRunning -> RootTailscaleMode.USERSPACE_SERVE
            daemonRunning -> RootTailscaleMode.USERSPACE
            else -> RootTailscaleMode.STOPPED
        }

    val authenticated: Boolean get() = tailnetIpv4 != null && backendOnline
    val hasSavedIdentity: Boolean get() = identitySaved || authenticated
    val kernelReady: Boolean
        get() = mode == RootTailscaleMode.KERNEL_TUN && backendOnline && authenticated && routeReady
    val userspaceServeReady: Boolean
        get() = mode == RootTailscaleMode.USERSPACE_SERVE && backendOnline && authenticated &&
            serveAdbReady && adb5555Listening
    val managementReady: Boolean get() = kernelReady || userspaceServeReady
    val officialVpnActive: Boolean get() = androidVpnOwner == OFFICIAL_TAILSCALE_PACKAGE
    val hiddifyVpnActive: Boolean get() = androidVpnOwner == HIDDIFY_PACKAGE

    companion object {
        const val OFFICIAL_TAILSCALE_PACKAGE = "com.tailscale.ipn"
        const val HIDDIFY_PACKAGE = "app.hiddify.com"
    }
}

enum class RootTailscaleActionCode {
    NO_ROOT,
    RUNTIME_MISSING,
    RUNTIME_INSTALLED,
    RUNTIME_INSTALL_FAILED,
    AUTH_REQUIRED,
    AUTH_STARTED,
    AUTH_ALREADY_COMPLETE,
    USERSPACE_SERVE_ENABLED,
    USERSPACE_SERVE_FAILED,
    ENABLED,
    ENABLE_FAILED,
    DISABLED,
    DISABLE_FAILED,
    REPAIRED,
    REPAIR_FAILED,
    BOOT_ENABLED,
    BOOT_DISABLED,
    BOOT_CHANGE_FAILED,
    OFFICIAL_APP_STOPPED,
    OFFICIAL_APP_STOP_FAILED,
}

data class RootTailscaleActionResult(
    val success: Boolean,
    val code: RootTailscaleActionCode,
    val snapshot: RootTailscaleSnapshot,
    val authUrl: String? = null,
    val detail: String? = null,
)

data class RootTailscaleDecision(
    val health: RootTailscaleHealth,
    val canInstallRuntime: Boolean,
    val canBeginAuthentication: Boolean,
    val canEnableUserspaceServe: Boolean,
    val canEnableRootOverlay: Boolean,
    val canDisableRootOverlay: Boolean,
    val canRepair: Boolean,
    val canEnableBoot: Boolean,
    val canStopOfficialApp: Boolean,
    val coexistenceReady: Boolean,
)
