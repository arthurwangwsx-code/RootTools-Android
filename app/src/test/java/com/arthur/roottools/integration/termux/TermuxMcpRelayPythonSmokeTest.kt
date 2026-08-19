package com.arthur.roottools.integration.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class TermuxMcpRelayPythonSmokeTest {
    private val script = TermuxMcpRelayScriptBuilder.build(
        deviceId = "123e4567-e89b-42d3-a456-426614174000",
        rootToolsAutomationToken = "a".repeat(64),
        relayBearerToken = "b".repeat(64),
    )

    @Test
    fun `generated relay compiles with host python when available`() {
        val python = findPython() ?: run {
            assumeTrue("python3 is unavailable on this host", false)
            return
        }
        val file = writeTempScript()
        try {
            val process = ProcessBuilder(python, "-m", "py_compile", file.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals("Generated Python failed syntax validation: $output", 0, process.waitFor())
        } finally {
            file.delete()
            File(file.parentFile, "__pycache__").deleteRecursively()
        }
    }

    @Test
    fun `stdio discover and tools list work without android`() {
        val python = findPython() ?: run {
            assumeTrue("python3 is unavailable on this host", false)
            return
        }
        val file = writeTempScript()
        try {
            val process = ProcessBuilder(python, file.absolutePath, "--transport", "stdio")
                .redirectErrorStream(true)
                .start()
            process.outputStream.bufferedWriter().use { writer ->
                writer.appendLine(request("discover-1", "server/discover", ""))
                writer.appendLine(request("list-1", "tools/list", ""))
            }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor())
            assertTrue(output.contains("\"supportedVersions\":[\"2026-07-28\"]"))
            assertTrue(output.contains("\"name\":\"get_device_status\""))
            assertTrue(output.contains("\"name\":\"freeze_app\""))
            assertTrue(output.contains("\"resultType\":\"complete\""))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `http relay enforces bearer and mcp header matching`() {
        val python = findPython() ?: run {
            assumeTrue("python3 is unavailable on this host", false)
            return
        }
        val file = writeTempScript()
        val process = ProcessBuilder(python, file.absolutePath, "--transport", "http", "--bind", "loopback")
            .redirectErrorStream(true)
            .start()
        try {
            assumeTrue("Port ${TermuxMcpRelayScriptBuilder.HTTP_PORT} is unavailable", waitForRelay())

            val body = request("discover-http", "server/discover", "")
            val unauthorized = post(body, bearer = null, methodHeader = "server/discover")
            assertEquals(401, unauthorized.first)

            val mismatch = post(body, bearer = "b".repeat(64), methodHeader = "tools/list")
            assertEquals(400, mismatch.first)
            assertTrue(mismatch.second.contains("HeaderMismatch"))

            val success = post(body, bearer = "b".repeat(64), methodHeader = "server/discover")
            assertEquals(200, success.first)
            assertTrue(success.second.contains("\"supportedVersions\":[\"2026-07-28\"]"))
        } finally {
            process.destroyForcibly()
            process.waitFor()
            file.delete()
        }
    }

    private fun request(id: String, method: String, extraParams: String): String =
        """{"jsonrpc":"2.0","id":"$id","method":"$method","params":{${extraParams}"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientInfo":{"name":"test","version":"1"},"io.modelcontextprotocol/clientCapabilities":{}}}}"""

    private fun writeTempScript(): File = kotlin.io.path.createTempFile("roottools-mcp-", ".py")
        .toFile()
        .apply { writeText(script) }

    private fun findPython(): String? {
        val process = runCatching { ProcessBuilder("/usr/bin/env", "python3", "--version").start() }.getOrNull()
            ?: return null
        return if (process.waitFor() == 0) "python3" else null
    }

    private fun waitForRelay(): Boolean {
        repeat(25) {
            try {
                val connection = URL("http://127.0.0.1:${TermuxMcpRelayScriptBuilder.HTTP_PORT}/mcp")
                    .openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 100
                connection.readTimeout = 100
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write("{}".toByteArray()) }
                connection.responseCode
                connection.disconnect()
                return true
            } catch (_: Exception) {
                Thread.sleep(80)
            }
        }
        return false
    }

    private fun post(body: String, bearer: String?, methodHeader: String): Pair<Int, String> {
        val connection = URL("http://127.0.0.1:${TermuxMcpRelayScriptBuilder.HTTP_PORT}/mcp")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 1_000
        connection.readTimeout = 2_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json, text/event-stream")
        connection.setRequestProperty("MCP-Protocol-Version", "2026-07-28")
        connection.setRequestProperty("Mcp-Method", methodHeader)
        bearer?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        connection.outputStream.use { it.write(body.toByteArray()) }
        val status = connection.responseCode
        val stream = if (status >= 400) connection.errorStream else connection.inputStream
        val response = stream?.bufferedReader()?.readText().orEmpty()
        connection.disconnect()
        return status to response
    }
}

