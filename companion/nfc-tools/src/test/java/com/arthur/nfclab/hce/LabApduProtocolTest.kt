package com.arthur.nfclab.hce

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabApduProtocolTest {
    @Test
    fun selectOurAidReturnsSuccess() {
        val select = hex("00A4040008${LabApduProtocol.AID}00")
        assertArrayEquals(hex("9000"), LabApduProtocol.respond(select, "hello"))
    }

    @Test
    fun getDataReturnsConfiguredPayloadAndSuccessWord() {
        val response = LabApduProtocol.respond(hex("80CA000000"), "hello")
        assertTrue(response.copyOfRange(0, 5).contentEquals("hello".toByteArray()))
        assertArrayEquals(hex("9000"), response.copyOfRange(response.size - 2, response.size))
    }

    @Test
    fun unknownAidDoesNotMatch() {
        val select = hex("00A4040008F00102030405060800")
        assertArrayEquals(hex("6A82"), LabApduProtocol.respond(select, "hello"))
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

