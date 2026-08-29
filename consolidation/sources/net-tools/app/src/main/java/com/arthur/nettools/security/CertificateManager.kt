package com.arthur.nettools.security

import android.content.Context
import com.arthur.nettools.capture.RootShell
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.Date
import java.util.Base64
import javax.security.auth.x500.X500Principal

data class CaStatus(
    val generated: Boolean,
    val subject: String? = null,
    val fingerprint: String? = null,
    val systemModuleInstalled: Boolean = false,
    val systemTrusted: Boolean = false,
    val requiresReboot: Boolean = false,
    val certificateFile: String? = null,
    val source: String? = null,
    val notAfter: Long? = null,
)

class CertificateManager(private val context: Context) {
    private val dir = File(context.filesDir, "ca").apply { mkdirs() }
    private val certFile = File(dir, "nettools-ca.pem")
    private val keyFile = File(dir, "nettools-ca.pk8")
    private val sourceFile = File(dir, "source.txt")
    private val exportCert = File(context.getExternalFilesDir(null), "certificates/nettools-ca.pem")

    fun status(): CaStatus {
        val cert = loadCertificate()
        val hash = cert?.let(::subjectHashOld)
        val staged = hash?.let {
            RootShell.exec("test -f /data/adb/modules/nettools_ca/system/etc/security/cacerts/$it.0 && echo yes").output.contains("yes")
        } ?: false
        val trusted = hash?.let {
            RootShell.exec("test -f /system/etc/security/cacerts/$it.0 && echo yes").output.contains("yes") ||
                RootShell.exec("test -f /apex/com.android.conscrypt/cacerts/$it.0 && echo yes").output.contains("yes")
        } ?: false
        return CaStatus(
            generated = cert != null,
            subject = cert?.subjectX500Principal?.name,
            fingerprint = cert?.encoded?.let { sha256(it) },
            systemModuleInstalled = staged,
            systemTrusted = trusted,
            requiresReboot = staged != trusted,
            certificateFile = exportCert.takeIf { it.exists() }?.absolutePath,
            source = sourceFile.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotBlank() },
            notAfter = cert?.notAfter?.time,
        )
    }

    fun importPem(pem: String, source: String = "MITM add-on"): CaStatus {
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(pem.byteInputStream()) as X509Certificate
        cert.checkValidity()
        val normalized = toPem(cert)
        certFile.writeText(normalized)
        keyFile.delete()
        sourceFile.writeText(source)
        exportCert.parentFile?.mkdirs()
        exportCert.writeText(normalized)
        return status()
    }

    fun generate(): CaStatus {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        val now = Date()
        val expiry = Calendar.getInstance().apply { time = now; add(Calendar.YEAR, 10) }.time
        val principal = X500Principal("CN=Net Tools Local CA,O=Net Tools")
        val serial = BigInteger(63, SecureRandom()).max(BigInteger.ONE)
        val algId = Der.sequence(Der.oidSha256WithRsa(), Der.nullValue())
        val tbs = Der.sequence(
            Der.context0(Der.integer(BigInteger.valueOf(2))),
            Der.integer(serial),
            algId,
            principal.encoded,
            Der.sequence(Der.utcTime(now), Der.utcTime(expiry)),
            principal.encoded,
            pair.public.encoded,
            Der.context3(Der.sequence(
                Der.sequence(Der.oid("2.5.29.19"), Der.boolean(true), Der.octetString(Der.sequence(Der.boolean(true)))),
                Der.sequence(Der.oid("2.5.29.15"), Der.boolean(true), Der.octetString(Der.bitString(byteArrayOf(0x06), 1))),
            )),
        )
        val signer = Signature.getInstance("SHA256withRSA").apply { initSign(pair.private); update(tbs) }
        val certDer = Der.sequence(tbs, algId, Der.bitString(signer.sign(), 0))
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(certDer.inputStream()) as X509Certificate
        cert.verify(pair.public)
        certFile.writeText(toPem(cert))
        keyFile.writeBytes(pair.private.encoded)
        sourceFile.writeText("Net Tools standalone CA")
        exportCert.parentFile?.mkdirs()
        exportCert.writeText(toPem(cert))
        return status()
    }

    fun installSystemModule(): Result<CaStatus> = runCatching {
        val cert = loadCertificate() ?: error("Generate the CA first")
        val hash = subjectHashOld(cert)
        val staging = File(context.getExternalFilesDir(null), "certificates/$hash.0").apply {
            parentFile?.mkdirs()
            writeText(toPem(cert))
        }
        val module = "/data/adb/modules/nettools_ca"
        val command = """
            mkdir -p '$module/system/etc/security/cacerts' &&
            cat > '$module/module.prop' <<'EOF'
id=nettools_ca
name=Net Tools System CA
version=1.0
versionCode=1
author=Net Tools
description=Reversible system trust overlay for local traffic inspection
EOF
            cp '${staging.absolutePath}' '$module/system/etc/security/cacerts/$hash.0' &&
            chmod 0644 '$module/system/etc/security/cacerts/$hash.0' &&
            chown 0:0 '$module/system/etc/security/cacerts/$hash.0'
        """.trimIndent()
        val result = RootShell.exec(command)
        check(result.code == 0) { result.output.ifBlank { "Root install failed" } }
        status()
    }

    fun removeSystemModule(): Result<Unit> = runCatching {
        val r = RootShell.exec("rm -rf /data/adb/modules/nettools_ca")
        check(r.code == 0) { r.output }
    }

    private fun loadCertificate(): X509Certificate? = runCatching {
        if (!certFile.exists()) return null
        CertificateFactory.getInstance("X.509").generateCertificate(certFile.inputStream()) as X509Certificate
    }.getOrNull()

    private fun subjectHashOld(cert: X509Certificate): String {
        val md = MessageDigest.getInstance("MD5").digest(cert.subjectX500Principal.encoded)
        return (3 downTo 0).joinToString("") { "%02x".format(md[it].toInt() and 0xFF) }
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString(":") { "%02X".format(it.toInt() and 0xFF) }

    private fun toPem(cert: X509Certificate): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(cert.encoded)
        return "-----BEGIN CERTIFICATE-----\n$body\n-----END CERTIFICATE-----\n"
    }
}

private object Der {
    fun sequence(vararg values: ByteArray) = tlv(0x30, values.fold(ByteArray(0)) { a, b -> a + b })
    fun context0(value: ByteArray) = tlv(0xA0, value)
    fun context3(value: ByteArray) = tlv(0xA3, value)
    fun integer(value: BigInteger) = tlv(0x02, value.toByteArray())
    fun boolean(value: Boolean) = tlv(0x01, byteArrayOf(if (value) 0xFF.toByte() else 0))
    fun nullValue() = byteArrayOf(0x05, 0x00)
    fun octetString(value: ByteArray) = tlv(0x04, value)
    fun bitString(value: ByteArray, unusedBits: Int) = tlv(0x03, byteArrayOf(unusedBits.toByte()) + value)
    fun oidSha256WithRsa() = oid("1.2.840.113549.1.1.11")
    fun utcTime(date: Date): ByteArray {
        val f = java.text.SimpleDateFormat("yyMMddHHmmss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        return tlv(0x17, f.format(date).toByteArray(Charsets.US_ASCII))
    }
    fun oid(value: String): ByteArray {
        val parts = value.split('.').map(String::toLong)
        val out = ByteArrayOutputStream()
        out.write((parts[0] * 40 + parts[1]).toInt())
        parts.drop(2).forEach { n0 ->
            var n = n0
            val stack = mutableListOf((n and 0x7F).toInt())
            n = n ushr 7
            while (n > 0) { stack += ((n and 0x7F).toInt() or 0x80); n = n ushr 7 }
            stack.asReversed().forEach(out::write)
        }
        return tlv(0x06, out.toByteArray())
    }
    private fun tlv(tag: Int, value: ByteArray) = byteArrayOf(tag.toByte()) + length(value.size) + value
    private fun length(size: Int): ByteArray = when {
        size < 128 -> byteArrayOf(size.toByte())
        size <= 0xFF -> byteArrayOf(0x81.toByte(), size.toByte())
        else -> byteArrayOf(0x82.toByte(), (size ushr 8).toByte(), size.toByte())
    }
}
