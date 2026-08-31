package com.arthur.roottools.feature.network.tailscale

import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootTailscaleCommandsTest {
    @Test
    fun generatedPrivilegedCommandsHaveValidShellSyntax() {
        val commands = listOf(
            RootTailscaleCommands.beginAuthentication("roottools-redmi"),
            RootTailscaleCommands.enableUserspaceServe("roottools-redmi"),
            RootTailscaleCommands.enableKernel("roottools-redmi"),
            RootTailscaleCommands.disable(),
            RootTailscaleCommands.enableBoot(RootTailscaleMode.USERSPACE_SERVE, "roottools-redmi"),
            RootTailscaleCommands.enableBoot(RootTailscaleMode.KERNEL_TUN, "roottools-redmi"),
            RootTailscaleCommands.disableBoot(),
        )

        commands.forEach { command ->
            val process = ProcessBuilder("sh", "-n", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertTrue(output, process.waitFor() == 0)
        }
    }

    @Test
    fun privilegedRuntimePathsArePrivateBeforeUse() {
        val commands = listOf(
            RootTailscaleCommands.beginAuthentication("roottools-redmi"),
            RootTailscaleCommands.enableUserspaceServe("roottools-redmi"),
            RootTailscaleCommands.enableKernel("roottools-redmi"),
            RootTailscaleCommands.enableBoot(RootTailscaleMode.USERSPACE_SERVE, "roottools-redmi"),
            RootTailscaleCommands.enableBoot(RootTailscaleMode.KERNEL_TUN, "roottools-redmi"),
        )

        commands.forEach { command ->
            assertTrue(command.contains("umask 077"))
            assertTrue(command.contains("chmod 0700 \"${'$'}BASE\" \"${'$'}BIN\" \"${'$'}RUN\" \"${'$'}STATE\""))
            assertTrue(command.indexOf("umask 077") < command.indexOf("mkdir -p \"${'$'}BASE\""))
        }
    }

    @Test
    fun authenticationReadsTheOfficialLoginUrlFromCommandOutput() {
        val command = RootTailscaleCommands.beginAuthentication("roottools-redmi")

        assertTrue(command.contains("login\\.tailscale\\.com"))
        assertTrue(command.contains("${'$'}RUN/auth.out"))
        assertTrue(command.contains("authenticated.marker"))
        assertTrue(command.contains("[ \"${'$'}backend\" = \"Running\" ]"))
        assertFalse(command.contains("am force-stop"))
    }

    @Test
    fun userspaceServeUsesFixedLocalManagementPorts() {
        val command = RootTailscaleCommands.enableUserspaceServe("roottools-redmi")

        assertTrue(command.contains("--tun=userspace-networking"))
        assertTrue(command.contains("--tcp=5555 tcp://127.0.0.1:5555"))
        assertTrue(command.contains("--tcp=8765 tcp://127.0.0.1:8765"))
        assertTrue(command.contains("authenticated.marker"))
        assertFalse(command.contains("am force-stop"))
        assertFalse(command.contains("-A OUTPUT"))
        assertFalse(command.contains("ip rule add"))
    }

    @Test
    fun kernelModeReliesOnTailscaleNativeRouting() {
        val command = RootTailscaleCommands.enableKernel("roottools-redmi")

        assertTrue(command.contains("--tun=tailscale0"))
        assertTrue(command.contains("ip route get 100.100.100.100"))
        assertFalse(command.contains("ip route replace table 1099"))
        assertFalse(command.contains("-A OUTPUT"))
        assertFalse(command.contains("ip rule add"))
        assertFalse(command.contains("am force-stop"))
    }

    @Test
    fun bootRestorePreservesOfficialVpnForBothModes() {
        val userspace = RootTailscaleCommands.enableBoot(RootTailscaleMode.USERSPACE_SERVE, "roottools-redmi")
        val kernel = RootTailscaleCommands.enableBoot(RootTailscaleMode.KERNEL_TUN, "roottools-redmi")

        assertTrue(userspace.contains("ROOTTOOLS_MODE=USERSPACE_SERVE"))
        assertTrue(userspace.contains("--tun=userspace-networking"))
        assertTrue(kernel.contains("ROOTTOOLS_MODE=KERNEL_TUN"))
        assertTrue(kernel.contains("--tun=tailscale0"))
        assertFalse(userspace.contains("am force-stop"))
        assertFalse(kernel.contains("am force-stop"))
    }

    @Test
    fun disableCleansDaemonAndLegacyOwnedRules() {
        val command = RootTailscaleCommands.disable()

        assertTrue(command.contains("--cleanup"))
        assertTrue(command.contains("table 1099"))
        assertTrue(command.contains("--set-mark 1099"))
        assertFalse(command.contains("kill ${'$'}(cat"))
        assertFalse(command.contains("rm -rf"))
    }
}
