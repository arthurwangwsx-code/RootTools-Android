package com.arthur.roottools.feature.network.inspection.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkCaptureCommandPolicyTest {
    @Test
    fun fixedBinaryLookupDoesNotAcceptArbitraryNames() {
        assertEquals("command -v tcpdump", NetworkCaptureCommandPolicy.commandPath(CaptureBinary.TCPDUMP))
    }

    @Test
    fun pcapdLaunchQuotesEveryPathAndAcceptsWholeDeviceUid() {
        val commands = NetworkCaptureCommandPolicy.pcapdLaunch(
            packagedPcapd = "/data/app/lib/libpcapd.so",
            packagedBridge = "/data/app/lib/libbridge.so",
            stagedPcapd = "/data/local/tmp/roottools-pcapd",
            stagedBridge = "/data/local/tmp/roottools-pcap-bridge",
            outputPcap = "/storage/emulated/0/Android/data/root/files/capture's.pcap",
            outputLog = "/storage/emulated/0/Android/data/root/files/capture.log",
            uid = null,
        )

        assertNotNull(commands)
        assertTrue(requireNotNull(commands).launch.contains("-1"))
        assertTrue(commands.launch.contains("capture'\"'\"'s.pcap"))
    }

    @Test
    fun hostileOrRelativePathsAreRejected() {
        val base = validPcapdLaunch()
        assertNotNull(base)
        assertNull(validPcapdLaunch(outputPcap = "relative/out.pcap"))
        assertNull(validPcapdLaunch(outputPcap = "/tmp/out.pcap\nreboot"))
        assertNull(validPcapdLaunch(outputPcap = "/tmp/out.pcap\u0000tail"))
    }

    @Test
    fun systemUidIsRejectedForPerAppCapture() {
        assertNull(validPcapdLaunch(uid = 1_000))
        assertNotNull(validPcapdLaunch(uid = 10_000))
    }

    @Test
    fun tcpdumpLaunchRequiresAbsolutePaths() {
        assertNull(NetworkCaptureCommandPolicy.tcpdumpLaunch("tcpdump", "/tmp/a.pcap", "/tmp/a.log", "/tmp/a.pid"))
        val command = NetworkCaptureCommandPolicy.tcpdumpLaunch(
            "/system/bin/tcpdump",
            "/tmp/a.pcap",
            "/tmp/a.log",
            "/tmp/a.pid",
        )
        assertNotNull(command)
        assertFalse(requireNotNull(command).contains("tcpdump -i"))
        assertTrue(command.startsWith("'/system/bin/tcpdump' -i"))
    }

    @Test
    fun processCommandsRequireTypedSignalAndValidPid() {
        assertEquals("kill -2 42 2>/dev/null", NetworkCaptureCommandPolicy.signal(42, CaptureSignal.INTERRUPT))
        assertEquals("kill -0 42 2>/dev/null", NetworkCaptureCommandPolicy.processAlive(42))
        assertNull(NetworkCaptureCommandPolicy.signal(1, CaptureSignal.KILL))
        assertNull(NetworkCaptureCommandPolicy.processAlive(-1))
    }

    private fun validPcapdLaunch(
        outputPcap: String = "/tmp/out.pcap",
        uid: Int? = 10_000,
    ): PcapdLaunchCommands? = NetworkCaptureCommandPolicy.pcapdLaunch(
        packagedPcapd = "/data/app/lib/libpcapd.so",
        packagedBridge = "/data/app/lib/libbridge.so",
        stagedPcapd = "/data/local/tmp/roottools-pcapd",
        stagedBridge = "/data/local/tmp/roottools-pcap-bridge",
        outputPcap = outputPcap,
        outputLog = "/tmp/out.log",
        uid = uid,
    )
}
