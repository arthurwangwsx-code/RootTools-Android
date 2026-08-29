package com.pcapdroid.mitm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class MitmAPIContractTest {
    @Test
    fun `wire constants remain compatible with add-on`() {
        assertEquals("com.pcapdroid.mitm", MitmAPI.PACKAGE_NAME)
        assertEquals("com.pcapdroid.mitm.MitmService", MitmAPI.MITM_SERVICE)
        assertEquals(1, MitmAPI.MSG_START_MITM)
        assertEquals(2, MitmAPI.MSG_GET_CA_CERTIFICATE)
        assertEquals(3, MitmAPI.MSG_STOP_MITM)
        assertEquals(4, MitmAPI.MSG_DISABLE_DOZE)
    }

    @Test
    fun `config remains Java serializable`() {
        val config = MitmAPI.MitmConfig().apply {
            proxyPort = 7780
            transparentMode = true
            sslInsecure = false
            shortPayload = true
            proxyAuth = null
            additionalOptions = ""
        }
        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { it.writeObject(config) }
            output.toByteArray()
        }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use {
            it.readObject() as MitmAPI.MitmConfig
        }
        assertEquals(7780, restored.proxyPort)
        assertTrue(restored.transparentMode)
        assertFalse(restored.sslInsecure)
        assertTrue(restored.shortPayload)
        assertNull(restored.proxyAuth)
    }
}
