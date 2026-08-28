package com.arthur.roottools.automation

/**
 * Trust boundary for semantic automation requests that already arrive over Android's ADB shell.
 *
 * ADB shell can already launch activities and inject input. Android's ActivityManager adds
 * FLAG_RECEIVER_FROM_SHELL for shell broadcasts and strips that flag when a non-root/non-shell
 * caller tries to forge it. Allowing only the typed Shadow Display and Agent Session command
 * families on that system-sanitized transport narrows authority instead of exposing a generic
 * shell surface.
 * All other automation commands continue to require a RootTools token/client scope.
 */
object AutomationTransportPolicy {
    // Hidden framework flag: Intent.FLAG_RECEIVER_FROM_SHELL.
    // Keep the numeric value local because the public SDK does not expose the constant.
    const val FLAG_RECEIVER_FROM_SHELL = 0x00400000

    fun isTrustedAdbRequest(intentFlags: Int, command: AutomationCommand): Boolean =
        intentFlags and FLAG_RECEIVER_FROM_SHELL != 0 && command.requiredScope in TRUSTED_ADB_SCOPES

    private val TRUSTED_ADB_SCOPES = setOf(
        AutomationScope.SHADOW_DISPLAY,
        AutomationScope.AGENT_SESSION,
    )
}
