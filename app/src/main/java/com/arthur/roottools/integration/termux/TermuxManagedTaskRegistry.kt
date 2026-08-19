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
    INSTALL_MCP_RELAY,
    VERIFY_MCP_RELAY,
    MCP_RELAY_STATUS,
    MCP_RELAY_START_LOOPBACK,
    MCP_RELAY_START_TAILSCALE,
    MCP_RELAY_STOP,
    POST_PROCESS_DIAGNOSTIC,
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

        TermuxManagedTaskId.INSTALL_MCP_RELAY -> TermuxCommandSpec(
            id = id,
            executable = "${'$'}PREFIX/bin/sh",
            arguments = listOf(
                "-c",
                "set -eu; umask 077; dir=\"${'$'}HOME/roottools/agent\"; target=\"${'$'}dir/roottools_mcp.py\"; tmp=\"${'$'}dir/.roottools_mcp.py.new\"; mkdir -p \"${'$'}dir\"; cat > \"${'$'}tmp\"; chmod 700 \"${'$'}tmp\"; mv -f \"${'$'}tmp\" \"${'$'}target\"; printf 'path=%s\\n' \"${'$'}target\"; sha256sum \"${'$'}target\" | awk '{print \"sha256=\"${'$'}1}'",
            ),
            timeoutMs = 15_000L,
            maxOutputChars = 4_000,
            label = "Install RootTools MCP relay",
            description = "Install the RootTools-generated MCP relay into a fixed private Termux path.",
            mutation = TermuxTaskMutation.WRITE_ROOTTOOLS_FILES,
            acceptsRootToolsStdin = true,
        )

        TermuxManagedTaskId.VERIFY_MCP_RELAY -> TermuxCommandSpec(
            id = id,
            executable = "${'$'}PREFIX/bin/sh",
            arguments = listOf(
                "-c",
                "target=\"${'$'}HOME/roottools/agent/roottools_mcp.py\"; [ -f \"${'$'}target\" ] || { echo installed=0; exit 4; }; echo installed=1; sha256sum \"${'$'}target\" | awk '{print \"sha256=\"${'$'}1}'",
            ),
            timeoutMs = 8_000L,
            maxOutputChars = 4_000,
            label = "Verify RootTools MCP relay",
            description = "Verify the installed RootTools MCP relay checksum.",
        )

        TermuxManagedTaskId.MCP_RELAY_STATUS -> TermuxCommandSpec(
            id = id,
            executable = "${'$'}PREFIX/bin/sh",
            arguments = listOf(
                "-c",
                "pidfile=\"${'$'}HOME/roottools/agent/roottools_mcp.pid\"; if [ ! -f \"${'$'}pidfile\" ]; then echo running=0; exit 0; fi; pid=${'$'}(cat \"${'$'}pidfile\" 2>/dev/null || true); case \"${'$'}pid\" in ''|*[!0-9]*) echo running=0; exit 0;; esac; if kill -0 \"${'$'}pid\" 2>/dev/null && tr '\\0' ' ' < \"/proc/${'$'}pid/cmdline\" 2>/dev/null | grep -q 'roottools_mcp.py'; then echo running=1; echo pid=\"${'$'}pid\"; else echo running=0; fi",
            ),
            timeoutMs = 8_000L,
            maxOutputChars = 4_000,
            label = "Read RootTools MCP relay status",
            description = "Check only the RootTools MCP relay pid file and matching process command line.",
        )

        TermuxManagedTaskId.MCP_RELAY_START_LOOPBACK -> relayStartTask(id, "loopback")
        TermuxManagedTaskId.MCP_RELAY_START_TAILSCALE -> relayStartTask(id, "tailscale")

        TermuxManagedTaskId.MCP_RELAY_STOP -> TermuxCommandSpec(
            id = id,
            executable = "${'$'}PREFIX/bin/sh",
            arguments = listOf(
                "-c",
                "set -eu; pidfile=\"${'$'}HOME/roottools/agent/roottools_mcp.pid\"; [ -f \"${'$'}pidfile\" ] || { echo stopped=1; exit 0; }; pid=${'$'}(cat \"${'$'}pidfile\"); case \"${'$'}pid\" in ''|*[!0-9]*) rm -f \"${'$'}pidfile\"; echo stopped=1; exit 0;; esac; if kill -0 \"${'$'}pid\" 2>/dev/null; then cmd=${'$'}(tr '\\0' ' ' < \"/proc/${'$'}pid/cmdline\" 2>/dev/null || true); echo \"${'$'}cmd\" | grep -q 'roottools_mcp.py' || { echo 'refusing to kill unrelated pid' >&2; exit 8; }; kill \"${'$'}pid\"; fi; rm -f \"${'$'}pidfile\"; echo stopped=1",
            ),
            timeoutMs = 8_000L,
            maxOutputChars = 4_000,
            label = "Stop RootTools MCP relay",
            description = "Stop only the process whose pid file still resolves to roottools_mcp.py.",
            mutation = TermuxTaskMutation.SERVICE_STATE,
        )

        TermuxManagedTaskId.POST_PROCESS_DIAGNOSTIC -> TermuxCommandSpec(
            id = id,
            executable = "${'$'}PREFIX/bin/python",
            arguments = listOf(
                "-c",
                "import hashlib,json,sys; data=sys.stdin.read(); lines=data.splitlines(); sections=[line.strip('_') for line in lines if line.startswith('__') and line.endswith('__') and len(line)>4]; out={'schemaVersion':1,'sha256':hashlib.sha256(data.encode()).hexdigest(),'bytes':len(data.encode()),'lines':len(lines),'sections':sections[:64]}; print(json.dumps(out,separators=(',',':'),sort_keys=True))",
            ),
            timeoutMs = 12_000L,
            maxOutputChars = 8_000,
            label = "Post-process RootTools diagnostic",
            description = "Normalize metadata from a RootTools-owned diagnostic snapshot using fixed Python code.",
            mutation = TermuxTaskMutation.READ_ONLY,
            acceptsRootToolsStdin = true,
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

    private fun relayStartTask(
        id: TermuxManagedTaskId,
        bindMode: String,
    ) = TermuxCommandSpec(
        id = id,
        executable = "${'$'}PREFIX/bin/sh",
        arguments = listOf(
            "-c",
            "set -eu; dir=\"${'$'}HOME/roottools/agent\"; target=\"${'$'}dir/roottools_mcp.py\"; pidfile=\"${'$'}dir/roottools_mcp.pid\"; log=\"${'$'}dir/roottools_mcp.log\"; [ -f \"${'$'}target\" ] || { echo 'relay not installed' >&2; exit 4; }; if [ -f \"${'$'}pidfile\" ]; then old=${'$'}(cat \"${'$'}pidfile\" 2>/dev/null || true); case \"${'$'}old\" in *[!0-9]*|'') old=0;; esac; if [ \"${'$'}old\" -gt 0 ] && kill -0 \"${'$'}old\" 2>/dev/null; then cmd=${'$'}(tr '\\0' ' ' < \"/proc/${'$'}old/cmdline\" 2>/dev/null || true); echo \"${'$'}cmd\" | grep -q 'roottools_mcp.py' && { echo already_running=1; echo pid=\"${'$'}old\"; exit 0; }; fi; fi; nohup \"${'$'}PREFIX/bin/python\" \"${'$'}target\" --transport http --bind $bindMode </dev/null >\"${'$'}log\" 2>&1 & pid=${'$'}!; echo \"${'$'}pid\" > \"${'$'}pidfile\"; sleep 1; kill -0 \"${'$'}pid\" 2>/dev/null || { tail -n 20 \"${'$'}log\" >&2 || true; rm -f \"${'$'}pidfile\"; exit 9; }; echo running=1; echo pid=\"${'$'}pid\"; echo bind=$bindMode",
        ),
        timeoutMs = 12_000L,
        maxOutputChars = 8_000,
        label = "Start RootTools MCP relay ($bindMode)",
        description = "Start the installed RootTools MCP relay with the fixed $bindMode binding policy.",
        mutation = TermuxTaskMutation.SERVICE_STATE,
    )
}

