package com.arthur.nfclab.hce

import com.arthur.nfclab.nfc.toHex

object LabApduProtocol {
    const val AID = "F001020304050607"
    private const val SELECT_PREFIX = "00A40400"
    private const val GET_DATA = "80CA000000"

    private val ok = byteArrayOf(0x90.toByte(), 0x00)
    private val notFound = byteArrayOf(0x6A, 0x82.toByte())
    private val wrongData = byteArrayOf(0x6A, 0x80.toByte())

    fun respond(command: ByteArray, payload: String): ByteArray {
        val hex = command.toHex()
        return when {
            isSelectForOurAid(hex) -> ok.copyOf()
            hex == GET_DATA -> payload.toByteArray(Charsets.UTF_8) + ok
            hex.startsWith(SELECT_PREFIX) -> notFound.copyOf()
            else -> wrongData.copyOf()
        }
    }

    private fun isSelectForOurAid(hex: String): Boolean {
        if (!hex.startsWith(SELECT_PREFIX)) return false
        if (hex.length < 10) return false
        val length = hex.substring(8, 10).toIntOrNull(16) ?: return false
        val dataStart = 10
        val dataEnd = dataStart + length * 2
        if (hex.length < dataEnd) return false
        return hex.substring(dataStart, dataEnd) == AID
    }
}

