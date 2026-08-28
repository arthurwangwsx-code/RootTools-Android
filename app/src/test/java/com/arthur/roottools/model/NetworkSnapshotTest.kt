package com.arthur.roottools.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSnapshotTest {
    @Test
    fun rootTailnetDoesNotRequireAndroidVpnTransport() {
        assertTrue(
            NetworkSnapshot(
                transports = setOf("WIFI"),
                tailscaleIpv4 = "100.100.20.30",
            ).tailscaleActive,
        )
    }

    @Test
    fun noTailnetAddressIsInactiveEvenWhenAndroidVpnExists() {
        assertFalse(NetworkSnapshot(transports = setOf("WIFI", "VPN")).tailscaleActive)
    }
}
