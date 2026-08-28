package com.arthur.roottools.feature.network.tailscale.data

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

internal object VerifiedTarGzExtractor {
    data class ExtractedRuntime(
        val tailscale: File,
        val tailscaled: File,
    )

    fun extractRuntime(archive: File, outputDir: File): ExtractedRuntime {
        outputDir.mkdirs()
        val tailscaleOutput = File(outputDir, "tailscale")
        val tailscaledOutput = File(outputDir, "tailscaled")
        tailscaleOutput.delete()
        tailscaledOutput.delete()

        GZIPInputStream(BufferedInputStream(FileInputStream(archive))).use { input ->
            extractTar(input, tailscaleOutput, tailscaledOutput)
        }
        check(tailscaleOutput.isFile && tailscaleOutput.length() > 0L) { "tailscale binary missing from verified archive" }
        check(tailscaledOutput.isFile && tailscaledOutput.length() > 0L) { "tailscaled binary missing from verified archive" }
        return ExtractedRuntime(tailscaleOutput, tailscaledOutput)
    }

    private fun extractTar(input: InputStream, tailscaleOutput: File, tailscaledOutput: File) {
        val header = ByteArray(TAR_BLOCK)
        while (true) {
            val read = input.readBlock(header)
            if (read == 0) return
            check(read == TAR_BLOCK) { "truncated tar header" }
            if (header.all { it == 0.toByte() }) return

            val name = header.asTarString(0, 100)
            val prefix = header.asTarString(345, 155)
            val fullName = if (prefix.isBlank()) name else "$prefix/$name"
            val size = header.asTarOctal(124, 12)
            val type = header[156].toInt().toChar()
            val target = when {
                fullName.endsWith("/tailscale") -> tailscaleOutput
                fullName.endsWith("/tailscaled") -> tailscaledOutput
                else -> null
            }

            if (target != null && (type == '\u0000' || type == '0')) {
                FileOutputStream(target).use { output -> input.copyExactly(output, size) }
            } else {
                input.skipExactly(size)
            }
            val padding = (TAR_BLOCK - (size % TAR_BLOCK)) % TAR_BLOCK
            input.skipExactly(padding)
        }
    }

    private fun InputStream.readBlock(buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val count = read(buffer, offset, buffer.size - offset)
            if (count < 0) break
            offset += count
        }
        return offset
    }

    private fun InputStream.copyExactly(output: FileOutputStream, byteCount: Long) {
        var remaining = byteCount
        val buffer = ByteArray(64 * 1024)
        while (remaining > 0L) {
            val count = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            check(count > 0) { "truncated tar entry" }
            output.write(buffer, 0, count)
            remaining -= count
        }
    }

    private fun InputStream.skipExactly(byteCount: Long) {
        var remaining = byteCount
        val buffer = ByteArray(8192)
        while (remaining > 0L) {
            val skipped = skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
                continue
            }
            val count = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            check(count > 0) { "truncated tar padding" }
            remaining -= count
        }
    }

    private fun ByteArray.asTarString(offset: Int, length: Int): String {
        val end = (offset until offset + length).firstOrNull { this[it] == 0.toByte() } ?: offset + length
        return copyOfRange(offset, end).toString(Charsets.US_ASCII).trim()
    }

    private fun ByteArray.asTarOctal(offset: Int, length: Int): Long {
        val value = asTarString(offset, length).trim().trim('\u0000', ' ')
        return if (value.isBlank()) 0L else value.toLong(radix = 8)
    }

    private const val TAR_BLOCK = 512
}

