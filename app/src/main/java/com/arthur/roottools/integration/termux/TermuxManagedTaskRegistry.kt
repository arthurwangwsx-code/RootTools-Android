package com.arthur.roottools.integration.termux

enum class TermuxManagedTaskId {
    TERMUX_INFO,
    PACKAGE_INVENTORY,
    RUNTIME_PROBE,
    GIT_VERSION,
    PYTHON_VERSION,
    NODE_VERSION,
    SSH_VERSION,
    INSTALL_ROOTTOOLS_CLI,
    VERIFY_ROOTTOOLS_CLI,
    INSTALL_DEVELOPER_PRESET,
    SSHD_CONFIG,
    SSHD_STATUS,
    SSHD_START,
    SSHD_STOP,
    SSHD_ENABLE_AUTOSTART,
    SSHD_DISABLE_AUTOSTART,
}

enum class TermuxTaskMutation {
    READ_ONLY,
    WRITE_ROOTTOOLS_FILES,
    INSTALL_PACKAGES,
    SERVICE_STATE,
    PERSISTENCE,
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
    val mutation: TermuxTaskMutation = TermuxTaskMutation.READ_ONLY,
    val acceptsRootToolsStdin: Boolean = false,
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

        TermuxManagedTaskId.INSTALL_ROOTTOOLS_CLI -> TermuxCommandSpec(
            id = id,
            executable = "${'$'}PREFIX/bin/sh",
            arguments = listOf(
                "-c",
                "set -eu; umask 077; target=\"${'$'}HOME/roottools/bin/roottools\"; tmp=\"${'$'}HOME/roottools/bin/.roottools.new\"; mkdir -p \"${'$'}HOME/roottools/bin\"; cat > \"${'$'}tmp\"; chmod 700 \"${'$'}tmp\"; mv -f \"${'$'}tmp\" \"${'$'}target\"; printf 'path=%s\\n' \"${'$'}target\"; sha256sum \"${'$'}target\" | awk '{print \"sha256=\"${'$'}1}'",
            ),
            timeoutMs = 15_000L,
            maxOutputChars = 4_000,
            label = "Install RootTools CLI",
            description = "Install the RootTools-generated scoped CLI into the fixed Termux user path.",
            mutation = TermuxTaskMutation.WRITE_ROOTTOOLS_FILES,
            acceptsRootToolsStdin = true,
        )

        TermuxManagedTaskId.VERIFY_ROOTTOOLS_CLI -> TermuxCommandSpec(
            id = id,
            executable = "${'$'}PREFIX/bin/sh",
            arguments = listOf(
                "-c",
                "target=\"${'$'}HOME/roottools/bin/roottools\"; [ -f \"${'$'}target\" ] || { echo installed=0; exit 4; }; echo installed=1; printf 'mode=%s\\n' \"${'$'}(stat -c '%a' \"${'$'}target\" 2>/dev/null || true)\"; sha256sum \"${'$'}target\" | awk '{print \"sha256=\"${'$'}1}'",
            ),
            timeoutMs = 8_000L,
            maxOutputChars = 4_000,
            label = "Verify RootTools CLI",
            description = "Verify the fixed RootTools CLI installation path and checksum.",
        )

        TermuxManagedTaskId.INSTALL_DEVELOPER_PRESET -> TermuxCommandSpec(
            id = id,
            executable = "${'$'}PREFIX/bin/pkg",
            arguments = listOf("install", "-y", "git", "openssh", "python", "nodejs-lts", "termux-services"),
            timeoutMs = 10 * 60_000L,
            maxOutputChars = 32_000,
            label = "Install RootTools developer preset",
            description = "Install the fixed RootTools developer package preset in Termux.",
            mutation = TermuxTaskMutation.INSTALL_PACKAGES,
        )

        TermuxManagedTaskId.SSHD_CONFIG -> TermuxCommandSpec(
            id = id,
            executable = "${'$'}PREFIX/bin/sh",
            arguments = listOf(
                "-c",
                "if command -v sshd >/dev/null 2>&1; then sshd -T 2>/dev/null | grep -E '^(port|listenaddress|passwordauthentication|pubkeyauthentication) ' | head -n 20; else echo openssh=0; exit 3; fi",
            ),
            timeoutMs = 8_000L,
            maxOutputChars = 8_000,
            label = "Read Termux sshd config",
            description = "Read a fixed safe subset of effective OpenSSH server configuration.",
        )

        TermuxManagedTaskId.SSHD_STATUS -> serviceTask(
            id = id,
            verb = "status",
            mutation = TermuxTaskMutation.READ_ONLY,
            label = "Read Termux sshd service",
        )

        TermuxManagedTaskId.SSHD_START -> serviceTask(
            id = id,
            verb = "up",
            mutation = TermuxTaskMutation.SERVICE_STATE,
            label = "Start Termux sshd",
        )

        TermuxManagedTaskId.SSHD_STOP -> serviceTask(
            id = id,
            verb = "down",
            mutation = TermuxTaskMutation.SERVICE_STATE,
            label = "Stop Termux sshd",
        )

        TermuxManagedTaskId.SSHD_ENABLE_AUTOSTART -> TermuxCommandSpec(
            id = id,
            executable = "${'$'}PREFIX/bin/sv-enable",
            arguments = listOf("sshd"),
            timeoutMs = 8_000L,
            maxOutputChars = 4_000,
            label = "Enable Termux sshd autostart",
            description = "Enable only the allowlisted sshd service through termux-services.",
            mutation = TermuxTaskMutation.PERSISTENCE,
        )

        TermuxManagedTaskId.SSHD_DISABLE_AUTOSTART -> TermuxCommandSpec(
            id = id,
            executable = "${'$'}PREFIX/bin/sv-disable",
            arguments = listOf("sshd"),
            timeoutMs = 8_000L,
            maxOutputChars = 4_000,
            label = "Disable Termux sshd autostart",
            description = "Disable only the allowlisted sshd service autostart.",
            mutation = TermuxTaskMutation.PERSISTENCE,
        )
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

    private fun serviceTask(
        id: TermuxManagedTaskId,
        verb: String,
        mutation: TermuxTaskMutation,
        label: String,
    ) = TermuxCommandSpec(
        id = id,
        executable = "${'$'}PREFIX/bin/sv",
        arguments = listOf(verb, "sshd"),
        timeoutMs = 8_000L,
        maxOutputChars = 4_000,
        label = label,
        description = "Manage only the allowlisted Termux sshd service.",
        mutation = mutation,
    )
}

