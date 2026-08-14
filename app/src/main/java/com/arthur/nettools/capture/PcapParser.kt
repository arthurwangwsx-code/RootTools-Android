package com.arthur.nettools.capture

import java.io.File
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PcapParser {
    private data class Key(val proto: String, val src: String, val dst: String, val host: String?, val hint: String?)

    fun parse(file: File, maxFlows: Int = 300, maxPackets: Int = 2_000): CaptureAnalysis {
        if (!file.exists() || file.length() < 24) return CaptureAnalysis()
        val bytes = file.readBytes()
        val magic = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.BIG_ENDIAN).int
        val nanosecondTimestamps = magic == 0xa1b23c4d.toInt() || magic == 0x4d3cb2a1
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
        val packetSummaries = mutableListOf<PacketSummary>()

        while (pos + 16 <= bytes.size) {
            val ph = ByteBuffer.wrap(bytes, pos, 16).order(order)
            val tsSeconds = ph.int.toLong() and 0xffffffffL
            val tsFraction = ph.int.toLong() and 0xffffffffL
            val captured = ph.int
            val original = ph.int
            pos += 16
            if (captured <= 0 || pos + captured > bytes.size) break
            packets++
            byteCount += original.coerceAtLeast(captured).toLong()
            val key = parsePacket(bytes, pos, captured, linkType)
            key?.let {
                protocols[key.proto] = (protocols[key.proto] ?: 0) + 1
                if (flows.size < maxFlows || flows.containsKey(key)) flows[key] = (flows[key] ?: 0) + 1
            } ?: run { protocols["Other"] = (protocols["Other"] ?: 0) + 1 }
            if (packetSummaries.size < maxPackets) {
                packetSummaries += inspectPacket(
                    data = bytes,
                    offset = pos,
                    length = captured,
                    linkType = linkType,
                    id = packets,
                    timestampMicros = tsSeconds * 1_000_000L + if (nanosecondTimestamps) tsFraction / 1_000L else tsFraction,
                    originalLength = original,
                    fallback = key,
                )
            }
            pos += captured
        }

        return CaptureAnalysis(
            packetCount = packets,
            byteCount = byteCount,
            protocols = protocols.entries.sortedByDescending { it.value }.map { ProtocolCount(it.key, it.value) },
            flows = flows.entries.sortedByDescending { it.value }.map {
                FlowSummary(it.key.proto, it.key.src, it.key.dst, it.key.host, it.key.hint, it.value)
            },
            packets = packetSummaries,
        )
    }

    private fun inspectPacket(
        data: ByteArray,
        offset: Int,
        length: Int,
        linkType: Int,
        id: Int,
        timestampMicros: Long,
        originalLength: Int,
        fallback: Key?,
    ): PacketSummary {
        val end = offset + length
        var ip = offset
        val linkLabel = when (linkType) {
            1 -> { ip += 14; "Ethernet" }
            113 -> { ip += 16; "Linux cooked" }
            276 -> { ip += 20; "Linux cooked v2" }
            12 -> "Raw IP"
            else -> "Link $linkType"
        }
        if (ip >= end) return genericPacket(id, timestampMicros, length, originalLength, fallback, listOf(PacketField("Link layer", linkLabel)), data, offset, end)

        val version = (data[ip].toInt() ushr 4) and 0xF
        val fields = mutableListOf(PacketField("Link layer", linkLabel))
        val network: String
        val srcIp: String
        val dstIp: String
        val transportOffset: Int
        val transportProto: Int
        if (version == 4 && ip + 20 <= end) {
            val ihl = (data[ip].toInt() and 0xF) * 4
            val totalLength = u16(data, ip + 2)
            transportProto = data[ip + 9].toInt() and 0xFF
            srcIp = ipv4(data, ip + 12); dstIp = ipv4(data, ip + 16); transportOffset = ip + ihl; network = "IPv4"
            fields += PacketField("IP version", "IPv4")
            fields += PacketField("TTL", (data[ip + 8].toInt() and 0xFF).toString())
            fields += PacketField("IP total length", "$totalLength B")
        } else if (version == 6 && ip + 40 <= end) {
            transportProto = data[ip + 6].toInt() and 0xFF
            srcIp = InetAddress.getByAddress(data.copyOfRange(ip + 8, ip + 24)).hostAddress ?: "::"
            dstIp = InetAddress.getByAddress(data.copyOfRange(ip + 24, ip + 40)).hostAddress ?: "::"
            transportOffset = ip + 40; network = "IPv6"
            fields += PacketField("IP version", "IPv6")
            fields += PacketField("Hop limit", (data[ip + 7].toInt() and 0xFF).toString())
            fields += PacketField("Payload length", "${u16(data, ip + 4)} B")
        } else return genericPacket(id, timestampMicros, length, originalLength, fallback, fields, data, offset, end)

        fields += PacketField("Source IP", srcIp)
        fields += PacketField("Destination IP", dstIp)
        if (transportProto == 1 || transportProto == 58) {
            val type = data.getOrNull(transportOffset)?.toInt()?.and(0xff)
            val code = data.getOrNull(transportOffset + 1)?.toInt()?.and(0xff)
            fields += PacketField("ICMP type", type?.toString() ?: "—")
            fields += PacketField("ICMP code", code?.toString() ?: "—")
            return packet(id, timestampMicros, length, originalLength, "ICMP", srcIp, dstIp, "$network ICMP", "Type ${type ?: "?"}, code ${code ?: "?"}", fields, data, transportOffset + 4, end)
        }
        if (transportOffset + 8 > end) return genericPacket(id, timestampMicros, length, originalLength, fallback, fields, data, offset, end)
        val srcPort = u16(data, transportOffset); val dstPort = u16(data, transportOffset + 2)
        val src = "$srcIp:$srcPort"; val dst = "$dstIp:$dstPort"
        fields += PacketField("Source port", srcPort.toString()); fields += PacketField("Destination port", dstPort.toString())

        if (transportProto == 17) {
            val udpLength = u16(data, transportOffset + 4)
            fields += PacketField("UDP length", "$udpLength B")
            val payload = transportOffset + 8
            if (srcPort == 53 || dstPort == 53) return inspectDns(data, payload, end, id, timestampMicros, length, originalLength, src, dst, fields)
            if (srcPort == 443 || dstPort == 443) return inspectQuic(data, payload, end, id, timestampMicros, length, originalLength, src, dst, fields)
            return packet(id, timestampMicros, length, originalLength, "UDP", src, dst, "UDP $srcPort → $dstPort", "$udpLength bytes", fields, data, payload, end)
        }
        if (transportProto != 6 || transportOffset + 20 > end) return genericPacket(id, timestampMicros, length, originalLength, fallback, fields, data, offset, end)
        val seq = u32(data, transportOffset + 4); val ack = u32(data, transportOffset + 8)
        val headerLength = ((data[transportOffset + 12].toInt() ushr 4) and 0xF) * 4
        val flags = data[transportOffset + 13].toInt() and 0xFF
        val window = u16(data, transportOffset + 14)
        val payload = transportOffset + headerLength
        fields += PacketField("TCP flags", tcpFlags(flags))
        fields += PacketField("Sequence", seq.toString()); fields += PacketField("Acknowledgement", ack.toString()); fields += PacketField("Window", window.toString())
        val httpLine = parseHttp(data, payload, end)
        if (httpLine != null) {
            fields += PacketField(if (httpLine.startsWith("HTTP/")) "Status line" else "Request line", httpLine)
            parseHttpHeaders(data, payload, end).forEach { (name, value) -> fields += PacketField(name, value) }
            return packet(id, timestampMicros, length, originalLength, "HTTP", src, dst, httpLine, "TCP · ${tcpFlags(flags)}", fields, data, payload, end)
        }
        val sni = parseTlsSni(data, payload, end)
        if (payload + 5 <= end && (data[payload].toInt() and 0xff) in 20..23) {
            val contentType = when (data[payload].toInt() and 0xff) { 20 -> "ChangeCipherSpec"; 21 -> "Alert"; 22 -> "Handshake"; 23 -> "Application Data"; else -> "TLS" }
            val tlsVersion = "0x%02x%02x".format(data[payload + 1].toInt() and 0xff, data[payload + 2].toInt() and 0xff)
            fields += PacketField("TLS content type", contentType); fields += PacketField("TLS record version", tlsVersion)
            if (sni != null) fields += PacketField("Server Name (SNI)", sni)
            return packet(id, timestampMicros, length, originalLength, "TLS", src, dst, sni ?: contentType, "TLS $contentType", fields, data, payload + 5, end)
        }
        return packet(id, timestampMicros, length, originalLength, "TCP", src, dst, "TCP $srcPort → $dstPort", tcpFlags(flags), fields, data, payload, end)
    }

    private fun inspectDns(d: ByteArray, start: Int, end: Int, id: Int, ts: Long, cap: Int, original: Int, src: String, dst: String, base: List<PacketField>): PacketSummary {
        val fields = base.toMutableList()
        val tx = if (start + 2 <= end) u16(d, start) else 0
        val flags = if (start + 4 <= end) u16(d, start + 2) else 0
        val query = parseDns(d, start, end)
        fields += PacketField("Transaction ID", "0x%04x".format(tx)); fields += PacketField("Message", if (flags and 0x8000 != 0) "Response" else "Query")
        if (query != null) fields += PacketField("Name", query)
        return packet(id, ts, cap, original, "DNS", src, dst, query ?: "DNS", if (flags and 0x8000 != 0) "Response" else "Query", fields, d, start, end)
    }

    private fun inspectQuic(d: ByteArray, start: Int, end: Int, id: Int, ts: Long, cap: Int, original: Int, src: String, dst: String, base: List<PacketField>): PacketSummary {
        val fields = base.toMutableList()
        val first = d.getOrNull(start)?.toInt()?.and(0xff) ?: 0
        val longHeader = first and 0x80 != 0
        fields += PacketField("QUIC header", if (longHeader) "Long header" else "Short header")
        if (longHeader && start + 5 <= end) fields += PacketField("QUIC version", "0x%08x".format(u32(d, start + 1)))
        return packet(id, ts, cap, original, "QUIC/UDP", src, dst, "QUIC ${if (longHeader) "Long Header" else "Short Header"}", "UDP/443", fields, d, start, end)
    }

    private fun packet(id: Int, ts: Long, cap: Int, original: Int, proto: String, src: String, dst: String, title: String, subtitle: String?, fields: List<PacketField>, data: ByteArray, payloadStart: Int, end: Int): PacketSummary {
        val safeStart = payloadStart.coerceIn(0, end)
        val sample = data.copyOfRange(safeStart, minOf(end, safeStart + 256))
        return PacketSummary(id, ts, cap, original, proto, src, dst, title, subtitle, fields, printableText(sample), hex(sample))
    }

    private fun genericPacket(id: Int, ts: Long, cap: Int, original: Int, fallback: Key?, fields: List<PacketField>, data: ByteArray, start: Int, end: Int) =
        packet(id, ts, cap, original, fallback?.proto ?: "Other", fallback?.src ?: "Unknown", fallback?.dst ?: "Unknown", fallback?.hint ?: fallback?.proto ?: "Packet", null, fields, data, start, end)

    private fun parseHttpHeaders(d: ByteArray, start: Int, end: Int): List<Pair<String, String>> {
        if (start >= end) return emptyList()
        return String(d, start, minOf(2048, end - start), Charsets.ISO_8859_1).substringBefore("\r\n\r\n").split("\r\n").drop(1).mapNotNull { line ->
            val idx = line.indexOf(':'); if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim().take(180)
        }.take(24)
    }

    private fun tcpFlags(flags: Int): String = buildList {
        if (flags and 0x01 != 0) add("FIN"); if (flags and 0x02 != 0) add("SYN"); if (flags and 0x04 != 0) add("RST"); if (flags and 0x08 != 0) add("PSH")
        if (flags and 0x10 != 0) add("ACK"); if (flags and 0x20 != 0) add("URG"); if (flags and 0x40 != 0) add("ECE"); if (flags and 0x80 != 0) add("CWR")
    }.joinToString(" · ").ifBlank { "None" }

    private fun printableText(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val printable = bytes.count { b -> (b.toInt() and 0xff) in 32..126 || b == '\n'.code.toByte() || b == '\r'.code.toByte() || b == '\t'.code.toByte() }
        if (printable < bytes.size * 0.72) return null
        return String(bytes, Charsets.UTF_8).replace("\u0000", "·").take(1200)
    }

    private fun hex(bytes: ByteArray): String? = bytes.takeIf { it.isNotEmpty() }?.toList()?.chunked(16)?.mapIndexed { row, chunk ->
        "%04x  %s".format(row * 16, chunk.joinToString(" ") { "%02x".format(it.toInt() and 0xff) })
    }?.joinToString("\n")

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
    private fun u32(d: ByteArray, p: Int): Long = ((d[p].toLong() and 0xff) shl 24) or ((d[p + 1].toLong() and 0xff) shl 16) or ((d[p + 2].toLong() and 0xff) shl 8) or (d[p + 3].toLong() and 0xff)
    private fun ipv4(d: ByteArray, p: Int) = (0..3).joinToString(".") { (d[p + it].toInt() and 0xFF).toString() }
}
