package com.arthur.roottools.feature.network.inspection.intercept

import com.arthur.roottools.root.RootShell

data class InterceptionRootActionResult(
    val success: Boolean,
    val technicalDetail: String? = null,
)

class InterceptionNetworkController(
    private val shell: RootShell,
    private val auditSink: InterceptionAuditSink? = null,
) {
    suspend fun installRules(
        uid: Int,
        proxyPort: Int,
        blockQuic: Boolean,
    ): InterceptionRootActionResult {
        val commands = InterceptionCommandPolicy.rules(uid, proxyPort, blockQuic)
            ?: return InterceptionRootActionResult(false, "invalid interception rule input")
        val cleanup = shell.execute(commands.cleanup, timeoutSeconds = RULE_TIMEOUT_SECONDS)
        if (!cleanup.success) {
            record("prepare_rules", uid, false, blockQuic)
            return InterceptionRootActionResult(false, cleanup.output)
        }
        val install = shell.execute(commands.install, timeoutSeconds = RULE_TIMEOUT_SECONDS)
        if (!install.success) {
            // One deterministic rollback, not an automatic retry of the requested mutation.
            shell.execute(commands.cleanup, timeoutSeconds = RULE_TIMEOUT_SECONDS)
        }
        record("install_rules", uid, install.success, blockQuic)
        return InterceptionRootActionResult(install.success, install.output.takeIf { !install.success })
    }

    suspend fun cleanupRules(): InterceptionRootActionResult {
        val result = shell.execute(
            InterceptionCommandPolicy.cleanupRules(),
            timeoutSeconds = RULE_TIMEOUT_SECONDS,
        )
        auditSink?.record(InterceptionAuditRecord(
            action = "cleanup_rules",
            success = result.success,
            rollbackHint = "No rollback required; interception chains remain absent",
        ))
        return InterceptionRootActionResult(result.success, result.output.takeIf { !result.success })
    }

    suspend fun forceStop(packageName: String): InterceptionRootActionResult {
        val command = InterceptionCommandPolicy.forceStop(packageName)
            ?: return InterceptionRootActionResult(false, "invalid package name")
        val result = shell.execute(command, timeoutSeconds = 5)
        auditSink?.record(InterceptionAuditRecord(
            action = "force_stop_target",
            target = packageName,
            success = result.success,
            rollbackHint = "Open the target app again",
        ))
        return InterceptionRootActionResult(result.success, result.output.takeIf { !result.success })
    }

    private fun record(action: String, uid: Int, success: Boolean, blockQuic: Boolean) {
        auditSink?.record(InterceptionAuditRecord(
            action = action,
            target = "uid=$uid",
            after = "proxy=true;blockQuic=$blockQuic",
            success = success,
            rollbackHint = "Stop interception or run cleanup to remove RootTools iptables chains",
        ))
    }

    private companion object {
        const val RULE_TIMEOUT_SECONDS = 8L
    }
}
