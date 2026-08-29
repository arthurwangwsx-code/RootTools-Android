package com.aibox.backgroundserver.platform.root

import com.arthur.roottools.core.privilege.BoundedProcessRunner
import com.arthur.roottools.core.privilege.RootCommandResult
import com.arthur.roottools.core.privilege.RootExecutionPolicy

class RootCommandGateway internal constructor(
    private val runner: BoundedProcessRunner = BoundedProcessRunner(),
) {
    internal fun execute(command: String, timeoutSeconds: Long = 5): RootCommandResult {
        val transport = RootExecutionPolicy.isolatedSuCommand(command, timeoutSeconds)
            ?: return RootCommandResult(2, "", "invalid root command input")
        return runner.run(
            command = transport,
            timeoutMillis = (timeoutSeconds + TRANSPORT_GRACE_SECONDS) * 1_000,
        )
    }

    internal fun probe(): RootCommandResult = execute("id")

    private companion object {
        const val TRANSPORT_GRACE_SECONDS = 2L
    }
}
