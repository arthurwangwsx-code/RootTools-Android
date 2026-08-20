package com.arthur.roottools.data

import com.arthur.roottools.model.AdbBootPolicy
import com.arthur.roottools.model.AdbEndpointType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbStateParserTest {
    @Test
    fun parsesRootTcpNativeWirelessAndEndpoints() {
        val raw = """
            __ROOT_TCP__
            PORT=5555
            __NATIVE__
            ENABLED=1
            SUPPORTED=true
            QR=true
            __USB__
            ADB_ENABLED=1
            ACTIVE=1
            __ADBD_PORTS__
            42387
            5555
            __NETWORK__
            TAILSCALE=100.91.126.56
            IFACE=wlan0
            LOCAL=192.168.1.20
            __TRUSTED__
            dev@mac.local
        """.trimIndent()

        val snapshot = AdbStateParser.parse(
            raw,
            rootAvailable = true,
            bootPolicy = AdbBootPolicy(restoreRootTcp = true),
            collectedAtMs = 1234L,
        )

        assertTrue(snapshot.rootTcpEnabled)
        assertEquals(5555, snapshot.rootTcpPort)
        assertTrue(snapshot.nativeWirelessEnabled)
        assertEquals(42387, snapshot.nativeTlsPort)
        assertTrue(snapshot.nativeWirelessQrSupported)
        assertTrue(snapshot.usbTransportActive)
        assertEquals(listOf("dev@mac.local"), snapshot.trustedHosts)
        assertTrue(snapshot.bootPolicy.restoreRootTcp)
        assertEquals(3, snapshot.endpoints.size)
        assertEquals(AdbEndpointType.TAILSCALE, snapshot.endpoints.first().type)
        assertTrue(snapshot.endpoints.first().recommended)
    }

    @Test
    fun doesNotTreatLegacyPortAsNativeTlsPort() {
        val raw = """
            __ROOT_TCP__
            PORT=5555
            __NATIVE__
            ENABLED=0
            SUPPORTED=true
            QR=true
            __USB__
            ADB_ENABLED=1
            ACTIVE=0
            __ADBD_PORTS__
            5555
            __NETWORK__
            TAILSCALE=
            IFACE=rmnet0
            LOCAL=10.0.0.5
            __TRUSTED__
        """.trimIndent()

        val snapshot = AdbStateParser.parse(raw, true, AdbBootPolicy(), 1L)

        assertTrue(snapshot.rootTcpEnabled)
        assertFalse(snapshot.nativeWirelessEnabled)
        assertEquals(null, snapshot.nativeTlsPort)
        assertEquals(1, snapshot.endpoints.size)
        assertEquals(AdbEndpointType.LOCAL_NETWORK, snapshot.endpoints.single().type)
    }
}
