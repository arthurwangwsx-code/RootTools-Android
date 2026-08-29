package com.arthur.roottools.feature.network.inspection.intercept

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificateCommandPolicyTest {
    @Test
    fun `certificate commands require subject hash and absolute staging path`() {
        assertNull(CertificateCommandPolicy.commands("../../data", "/tmp/ca.pem"))
        assertNull(CertificateCommandPolicy.commands("1234abcd", "relative/ca.pem"))
        assertNull(CertificateCommandPolicy.commands("1234abcd", "/tmp/ca.pem\nreboot"))
        assertNotNull(CertificateCommandPolicy.commands("1234abcd", "/tmp/ca.pem"))
    }

    @Test
    fun `install uses canonical reversible Magisk module`() {
        val commands = requireNotNull(CertificateCommandPolicy.commands("1234abcd", "/tmp/cert's.pem"))
        assertTrue(commands.install.contains("roottools_network_ca"))
        assertTrue(commands.install.contains("printf '%b\\n'"))
        assertTrue(commands.install.contains("cert'\"'\"'s.pem"))
        assertTrue(commands.install.contains("chmod 0644"))
        assertTrue(commands.remove.contains("roottools_network_ca"))
    }

    @Test
    fun `status and removal retain legacy module compatibility`() {
        val commands = requireNotNull(CertificateCommandPolicy.commands("1234abcd", "/tmp/ca.pem"))
        assertTrue(commands.stagedCheck.contains("nettools_ca"))
        assertTrue(commands.remove.contains("nettools_ca"))
        assertTrue(commands.trustedCheck.contains("com.android.conscrypt"))
    }
}
