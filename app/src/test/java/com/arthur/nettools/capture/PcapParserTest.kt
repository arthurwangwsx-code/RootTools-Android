package com.arthur.nettools.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcapParserTest {
    @Test
    fun `parses one raw ipv4 http packet`() {
        val payload = "GET / HTTP/1.1\r\nHost: example.com\r\n\r\n".toByteArray(Charsets.ISO_8859_1)
        val ip = ByteArray(20 + 20 + payload.size)
        ip[0] = 0x45
        val total = ip.size
        ip[2] = (total ushr 8).toByte(); ip[3] = total.toByte()
        ip[8] = 64; ip[9] = 6
        ip[12] = 10; ip[13] = 0; ip[14] = 0; ip[15] = 1
        ip[16] = 93; ip[17] = 0xb8.toByte(); ip[18] = 0xd8.toByte(); ip[19] = 34
        val tcp = 20
        ip[tcp] = 0x30; ip[tcp + 1] = 0x39
        ip[tcp + 2] = 0; ip[tcp + 3] = 80
        ip[tcp + 12] = 0x50
        payload.copyInto(ip, 40)

        val file = File.createTempFile("nettools", ".pcap")
        file.writeBytes(globalHeader() + packetHeader(ip.size) + ip)
        try {
            val analysis = PcapParser.parse(file)
            assertEquals(1, analysis.packetCount)
            assertEquals("HTTP", analysis.protocols.first().protocol)
            assertTrue(analysis.flows.first().hint?.startsWith("GET / HTTP/1.1") == true)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `empty file returns empty analysis`() {
        val file = File.createTempFile("nettools-empty", ".pcap")
        file.writeBytes(byteArrayOf())
        try { assertEquals(0, PcapParser.parse(file).packetCount) } finally { file.delete() }
    }

    private fun globalHeader(): ByteArray = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
        putInt(0xa1b2c3d4.toInt())
        putShort(2); putShort(4)
        putInt(0); putInt(0); putInt(65535); putInt(12)
    }.array()

    private fun packetHeader(size: Int): ByteArray = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
        putInt(1); putInt(0); putInt(size); putInt(size)
    }.array()
}
