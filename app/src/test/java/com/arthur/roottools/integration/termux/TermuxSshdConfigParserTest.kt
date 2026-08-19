package com.arthur.roottools.integration.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxSshdConfigParserTest {
    @Test
    fun `parses effective sshd security subset`() {
        val config = TermuxSshdConfigParser.parse(
            """
            port 8022
            listenaddress 0.0.0.0:8022
            listenaddress [::]:8022
            passwordauthentication yes
            pubkeyauthentication yes
            """.trimIndent()
        )

        assertEquals(8022, config.port)
        assertEquals(true, config.passwordAuthentication)
        assertEquals(true, config.publicKeyAuthentication)
        assertTrue(config.wildcardListenerConfigured)
    }

    @Test
    fun `invalid values remain unknown instead of being trusted`() {
        val config = TermuxSshdConfigParser.parse(
            "port 99999\npasswordauthentication maybe\nlistenaddress 127.0.0.1:8022"
        )
        assertEquals(null, config.port)
        assertEquals(null, config.passwordAuthentication)
        assertFalse(config.wildcardListenerConfigured)
    }
}

