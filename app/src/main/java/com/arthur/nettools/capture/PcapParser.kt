package com.arthur.nettools.capture

import java.io.File
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PcapParser {
    private data class Key(val proto: String, val src: String, val dst: String, val host: String?, val hint: String?)

    fun parse(file: File, maxFlows: Int = 300): CaptureAnalysis {
        if (!file.exists() || file.length() < 24) return CaptureAnalysis()
        val bytes = file.readBytes()
        val magic = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.BIG_ENDIAN).int
        val order = when (magic) {
            0xa1b2c3d4.toInt(), 0xa1b23c4d.toInt() -> ByteOrder.BIG_ENDIAN
            0xd4c3b2a1.toInt(), 0x4d3cb2a1 -> ByteOrder.LITTLE_ENDIAN
            else -> return CaptureAnalysis()
        }
        val global = ByteBuffer.wrap(bytes, 0, 24).order(order)
        global.position(20)
        val linkType = global.int
        var pos = 24
        var packets = 0
        var byteCount = 0L
        val protocols = linkedMapOf<String, Int>()
        val flows = linkedMapOf<Key, Int>()

        while (pos + 16 <= bytes.size) {
            val ph = ByteBuffer.wrap(bytes, pos, 16).order(order)
            ph.int; ph.int
            val captured = ph.int
            val original = ph.int
            pos += 16
            if (captured <= 0 || pos + captured > bytes.size) break
            packets++
            byteCount += original.coerceAtLeast(captured).toLong()
            parsePacket(bytes, pos, captured, linkType)?.let { key ->
                protocols[key.proto] = (protocols[key.proto] ?: 0) + 1
                if (flows.size < maxFlows || flows.containsKey(key)) flows[key] = (flows[key] ?: 0) + 1
            } ?: run { protocols["Other"] = (protocols["Other"] ?: 0) + 1 }
            pos += captured
        }

        return CaptureAnalysis(
            packetCount = packets,
            byteCount = byteCount,
            protocols = protocols.entries.sortedByDescending { it.value }.map { ProtocolCount(it.key, it.value) },
            flows = flows.entries.sortedByDescending { it.value }.map {
                FlowSummary(it.key.proto, it.key.src, it.key.dst, it.key.host, it.key.hint, it.value)
            },
        )
    }

    private fun parsePacket(data: ByteArray, offset: Int, length: Int, linkType: Int): Key? {
        var ip = offset
        when (linkType) {
            1 -> {
                if (length < 14) return null
                val etherType = u16(data, offset + 12)
                if (etherType != 0x0800 && etherType != 0x86dd) return null
                ip += 14
            }
            113 -> {
                if (length < 16) return null
                val proto = u16(data, offset + 14)
                if (proto != 0x0800 && proto != 0x86dd) return null
                ip += 16
            }
            276 -> {
                if (length < 20) return null
                val proto = u16(data, offset)
                if (proto != 0x0800 && proto != 0x86dd) return null
                ip += 20
            }
            12 -> Unit
            else -> return null
        }
        if (ip >= offset + length) return null
        return when ((data[ip].toInt() ushr 4) and 0xF) {
            4 -> parseIpv4(data, ip, offset + length)
            6 -> parseIpv6(data, ip, offset + length)
            else -> null
        }
    }

    private fun parseIpv4(d: ByteArray, ip: Int, end: Int): Key? {
        if (ip + 20 > end) return null
        val ihl = (d[ip].toInt() and 0xF) * 4
        if (ihl < 20 || ip + ihl > end) return null
        val proto = d[ip + 9].toInt() and 0xFF
        val srcIp = ipv4(d, ip + 12)
        val dstIp = ipv4(d, ip + 16)
        return parseTransport(d, ip + ihl, end, proto, srcIp, dstIp)
    }

    private fun parseIpv6(d: ByteArray, ip: Int, end: Int): Key? {
        if (ip + 40 > end) return null
        val proto = d[ip + 6].toInt() and 0xFF
        val src = InetAddress.getByAddress(d.copyOfRange(ip + 8, ip + 24)).hostAddress ?: "::"
        val dst = InetAddress.getByAddress(d.copyOfRange(ip + 24, ip + 40)).hostAddress ?: "::"
        return parseTransport(d, ip + 40, end, proto, src, dst)
    }

    private fun parseTransport(d: ByteArray, p: Int, end: Int, proto: Int, srcIp: String, dstIp: String): Key? {
        if (proto == 1 || proto == 58) return Key("ICMP", srcIp, dstIp, null, null)
        if (p + 8 > end) return Key("IP/$proto", srcIp, dstIp, null, null)
        val srcPort = u16(d, p)
        val dstPort = u16(d, p + 2)
        if (proto == 17) {
            val payload = p + 8
            if (srcPort == 53 || dstPort == 53) {
                val q = parseDns(d, payload, end)
                return Key("DNS", "$srcIp:$srcPort", "$dstIp:$dstPort", q, q?.let { "query $it" })
            }
            val name = if (srcPort == 443 || dstPort == 443) "QUIC/UDP" else "UDP"
            return Key(name, "$srcIp:$srcPort", "$dstIp:$dstPort", null, null)
        }
        if (proto != 6 || p + 20 > end) return Key("IP/$proto", srcIp, dstIp, null, null)
        val tcpHeader = ((d[p + 12].toInt() ushr 4) and 0xF) * 4
        val payload = p + tcpHeader
        if (payload > end) return Key("TCP", "$srcIp:$srcPort", "$dstIp:$dstPort", null, null)
        val http = parseHttp(d, payload, end)
        if (http != null) return Key("HTTP", "$srcIp:$srcPort", "$dstIp:$dstPort", null, http)
        val sni = parseTlsSni(d, payload, end)
        val label = if (srcPort == 443 || dstPort == 443 || sni != null) "TLS" else "TCP"
        return Key(label, "$srcIp:$srcPort", "$dstIp:$dstPort", sni, sni?.let { "SNI $it" })
    }

    private fun parseDns(d: ByteArray, start: Int, end: Int): String? {
        if (start + 12 >= end) return null
        var p = start + 12
        val parts = mutableListOf<String>()
        repeat(24) {
            if (p >= end) return@repeat
            val n = d[p].toInt() and 0xFF
            p++
            if (n == 0) return parts.takeIf { it.isNotEmpty() }?.joinToString(".")
            if (n > 63 || p + n > end) return null
            parts += String(d, p, n, Charsets.UTF_8)
            p += n
        }
        return null
    }

    private fun parseHttp(d: ByteArray, start: Int, end: Int): String? {
        if (end - start < 8) return null
        val count = minOf(180, end - start)
        val s = String(d, start, count, Charsets.ISO_8859_1)
        val line = s.substringBefore("\r\n")
        val verbs = listOf("GET ", "POST ", "PUT ", "DELETE ", "PATCH ", "HEAD ", "OPTIONS ", "HTTP/")
        return line.takeIf { l -> verbs.any { l.startsWith(it) } }?.take(160)
    }

    private fun parseTlsSni(d: ByteArray, start: Int, end: Int): String? {
        // TLS record + ClientHello parser; intentionally ignores fragmented handshakes.
        if (start + 9 >= end || (d[start].toInt() and 0xFF) != 22 || (d[start + 5].toInt() and 0xFF) != 1) return null
        var p = start + 9
        if (p + 34 > end) return null
        p += 34
        if (p >= end) return null
        val sessionLen = d[p].toInt() and 0xFF; p += 1 + sessionLen
        if (p + 2 > end) return null
        val cipherLen = u16(d, p); p += 2 + cipherLen
        if (p >= end) return null
        val compLen = d[p].toInt() and 0xFF; p += 1 + compLen
        if (p + 2 > end) return null
        val extTotal = u16(d, p); p += 2
        val extEnd = minOf(end, p + extTotal)
        while (p + 4 <= extEnd) {
            val type = u16(d, p); val len = u16(d, p + 2); p += 4
            if (p + len > extEnd) return null
            if (type == 0 && len >= 5) {
                var q = p + 2
                if (q + 3 > p + len) return null
                val nameType = d[q].toInt() and 0xFF; q++
                val nameLen = u16(d, q); q += 2
                if (nameType == 0 && q + nameLen <= p + len) return String(d, q, nameLen, Charsets.US_ASCII)
            }
            p += len
        }
        return null
    }

    private fun u16(d: ByteArray, p: Int) = ((d[p].toInt() and 0xFF) shl 8) or (d[p + 1].toInt() and 0xFF)
    private fun ipv4(d: ByteArray, p: Int) = (0..3).joinToString(".") { (d[p + it].toInt() and 0xFF).toString() }
}
