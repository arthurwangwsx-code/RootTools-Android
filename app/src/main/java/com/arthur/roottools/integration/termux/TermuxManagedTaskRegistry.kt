package com.arthur.roottools.integration.termux

enum class TermuxManagedTaskId {
    TERMUX_INFO,
    PACKAGE_INVENTORY,
    RUNTIME_PROBE,
    GIT_VERSION,
    PYTHON_VERSION,
    NODE_VERSION,
    SSH_VERSION,
}

data class TermuxCommandSpec(
    val id: TermuxManagedTaskId,
    val executable: String,
    val arguments: List<String> = emptyList(),
    val workDir: String = TermuxRunCommandContract.TERMUX_HOME,
    val timeoutMs: Long = 15_000L,
    val maxOutputChars: Int = 24_000,
    val label: String,
    val description: String,
)

/**
 * Allowlisted command registry. No external caller can provide an executable path or raw shell
 * program. New execution behavior must be added here as a typed task first.
 */
object TermuxManagedTaskRegistry {
    fun spec(id: TermuxManagedTaskId): TermuxCommandSpec = when (id) {
        TermuxManagedTaskId.TERMUX_INFO -> TermuxCommandSpec(
            id = id,
            executable = "${'$'}PREFIX/bin/termux-info",
            timeoutMs = 20_000L,
            label = "RootTools Termux info",
            description = "Read Termux runtime information for RootTools Developer Runtime.",
        )

        TermuxManagedTaskId.PACKAGE_INVENTORY -> TermuxCommandSpec(
            id = id,
            executable = "${'$'}PREFIX/bin/pkg",
            arguments = listOf("list-installed"),
            timeoutMs = 25_000L,
            maxOutputChars = 48_000,
            label = "RootTools package inventory",
            description = "Read the installed Termux package inventory.",
        )

        TermuxManagedTaskId.RUNTIME_PROBE -> TermuxCommandSpec(
            id = id,
            executable = "${'$'}PREFIX/bin/sh",
            arguments = listOf(
                "-c",
                "for x in git python node ssh sshd sv; do if command -v \"${'$'}x\" >/dev/null 2>&1; then printf '%s=1\\n' \"${'$'}x\"; else printf '%s=0\\n' \"${'$'}x\"; fi; done",
            ),
            label = "RootTools runtime probe",
            description = "Probe a fixed allowlist of developer runtime executables.",
        )

        TermuxManagedTaskId.GIT_VERSION -> versionTask(id, "git", listOf("--version"))
        TermuxManagedTaskId.PYTHON_VERSION -> versionTask(id, "python", listOf("--version"))
        TermuxManagedTaskId.NODE_VERSION -> versionTask(id, "node", listOf("--version"))
        TermuxManagedTaskId.SSH_VERSION -> versionTask(id, "ssh", listOf("-V"))
    }

    private fun versionTask(
        id: TermuxManagedTaskId,
        executableName: String,
        arguments: List<String>,
    ) = TermuxCommandSpec(
        id = id,
        executable = "${'$'}PREFIX/bin/$executableName",
        arguments = arguments,
        timeoutMs = 8_000L,
        maxOutputChars = 4_000,
        label = "RootTools $executableName version",
        description = "Read the installed $executableName version.",
    )
}

