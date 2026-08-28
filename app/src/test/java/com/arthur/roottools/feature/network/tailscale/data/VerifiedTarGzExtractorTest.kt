package com.arthur.roottools.feature.network.tailscale.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

class VerifiedTarGzExtractorTest {
    @Test
    fun extractsOnlyExpectedRuntimeEntries() {
        val root = Files.createTempDirectory("tailscale-tar-test").toFile()
        val archive = File(root, "runtime.tgz")
        writeTarGz(
            archive,
            mapOf(
                "tailscale_1.102.3_arm64/tailscale" to "client-binary".toByteArray(),
                "tailscale_1.102.3_arm64/tailscaled" to "daemon-binary".toByteArray(),
                "tailscale_1.102.3_arm64/ignored.txt" to "ignored".toByteArray(),
            ),
        )

        val output = File(root, "out")
        val extracted = VerifiedTarGzExtractor.extractRuntime(archive, output)

        assertEquals("client-binary", extracted.tailscale.readText())
        assertEquals("daemon-binary", extracted.tailscaled.readText())
        assertFalse(File(output, "ignored.txt").exists())
    }

    @Test
    fun rejectsArchiveWithoutDaemon() {
        val root = Files.createTempDirectory("tailscale-tar-missing").toFile()
        val archive = File(root, "runtime.tgz")
        writeTarGz(archive, mapOf("pkg/tailscale" to "client".toByteArray()))

        val failure = runCatching { VerifiedTarGzExtractor.extractRuntime(archive, File(root, "out")) }

        assertTrue(failure.isFailure)
    }

    private fun writeTarGz(file: File, entries: Map<String, ByteArray>) {
        GZIPOutputStream(FileOutputStream(file)).use { gzip ->
            entries.forEach { (name, data) ->
                val header = ByteArray(512)
                writeAscii(header, 0, 100, name)
                writeAscii(header, 100, 8, "0000755\u0000")
                writeAscii(header, 108, 8, "0000000\u0000")
                writeAscii(header, 116, 8, "0000000\u0000")
                writeAscii(header, 124, 12, data.size.toString(8).padStart(11, '0') + "\u0000")
                writeAscii(header, 136, 12, "00000000000\u0000")
                repeat(8) { header[148 + it] = ' '.code.toByte() }
                header[156] = '0'.code.toByte()
                writeAscii(header, 257, 6, "ustar\u0000")
                var checksum = header.sumOf { it.toUByte().toInt() }
                writeAscii(header, 148, 8, checksum.toString(8).padStart(6, '0') + "\u0000 ")
                gzip.write(header)
                gzip.write(data)
                val padding = (512 - (data.size % 512)) % 512
                if (padding > 0) gzip.write(ByteArray(padding))
            }
            gzip.write(ByteArray(1024))
        }
    }

    private fun writeAscii(target: ByteArray, offset: Int, length: Int, value: String) {
        val bytes = value.toByteArray(Charsets.US_ASCII)
        require(bytes.size <= length)
        bytes.copyInto(target, destinationOffset = offset)
    }
}

