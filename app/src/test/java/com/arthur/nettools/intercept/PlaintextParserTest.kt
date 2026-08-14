package com.arthur.nettools.intercept

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaintextParserTest {
    @Test
    fun `redacts common credentials from preview`() {
        val text = """
            GET /v1/items?access_token=very-secret HTTP/1.1\r
            Host: example.com\r
            Authorization: Bearer another-secret\r
            Cookie: sid=cookie-secret\r
            Content-Type: application/json\r
            \r
            {}
        """.trimIndent().replace("\\r", "\r")
        val preview = PlaintextParser.preview(DecryptedKind.HTTP_REQUEST, text.toByteArray()).second
        assertFalse(preview.contains("very-secret"))
        assertFalse(preview.contains("another-secret"))
        assertFalse(preview.contains("cookie-secret"))
        assertTrue(preview.contains("<redacted>"))
    }

    @Test
    fun `parses request metadata`() {
        val text = "GET /hello HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/json\r\n\r\n{}"
        val parsed = PlaintextParser.parseHttp(DecryptedKind.HTTP_REQUEST, text.toByteArray())!!
        assertEquals("GET", parsed.method)
        assertEquals("example.com", parsed.host)
        assertEquals("application/json", parsed.contentType)
        assertEquals("GET /hello HTTP/1.1", parsed.firstLine)
    }

    @Test
    fun `parses response status`() {
        val text = "HTTP/1.1 204 No Content\r\nContent-Type: text/plain\r\n\r\n"
        val parsed = PlaintextParser.parseHttp(DecryptedKind.HTTP_RESPONSE, text.toByteArray())!!
        assertEquals(204, parsed.statusCode)
        assertEquals("text/plain", parsed.contentType)
    }
}
