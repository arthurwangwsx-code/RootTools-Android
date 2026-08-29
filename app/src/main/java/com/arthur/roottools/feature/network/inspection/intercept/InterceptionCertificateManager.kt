package com.arthur.roottools.feature.network.inspection.intercept

import android.content.Context
import com.arthur.roottools.root.RootShell
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Calendar
import java.util.Date
import javax.security.auth.x500.X500Principal

class InterceptionCertificateManager(
    context: Context,
    private val shell: RootShell,
    private val auditSink: InterceptionAuditSink? = null,
) {
    private val directory = File(context.filesDir, "network-inspection/ca").apply { mkdirs() }
    private val certificateFile = File(directory, "roottools-network-ca.pem")
    private val privateKeyFile = File(directory, "roottools-network-ca.pk8")
    private val sourceFile = File(directory, "source.txt")
    private val exportedCertificate = File(
        context.getExternalFilesDir(null),
        "network-inspection/certificates/roottools-network-ca.pem",
    )

    suspend fun status(): InterceptionCertificateStatus {
        val certificate = loadCertificate()
        val hash = certificate?.let(::subjectHashOld)
        val commands = hash?.let { CertificateCommandPolicy.commands(it, exportedCertificate.absolutePath) }
        val staged = commands?.let { shell.execute(it.stagedCheck, timeoutSeconds = STATUS_TIMEOUT_SECONDS).success } ?: false
        val trusted = commands?.let { shell.execute(it.trustedCheck, timeoutSeconds = STATUS_TIMEOUT_SECONDS).success } ?: false
        return InterceptionCertificateStatus(
            available = certificate != null,
            subject = certificate?.subjectX500Principal?.name,
            fingerprint = certificate?.encoded?.let(::sha256Fingerprint),
            systemModuleInstalled = staged,
            systemTrusted = trusted,
            requiresReboot = staged != trusted,
            certificateFile = exportedCertificate.takeIf(File::exists)?.absolutePath,
            source = CertificateSource.fromWire(sourceFile.takeIf(File::exists)?.readText()?.trim()),
            notAfter = certificate?.notAfter?.time,
        )
    }

    suspend fun importPem(
        pem: String,
        source: CertificateSource = CertificateSource.MITM_ADDON,
    ): InterceptionCertificateStatus {
        require(source != CertificateSource.UNKNOWN) { "Certificate source must be explicit" }
        require(pem.length <= MAX_PEM_LENGTH) { "Certificate payload is too large" }
        val certificate = parseCertificate(pem)
        certificate.checkValidity()
        persist(certificate, source, privateKey = null)
        return status()
    }

    suspend fun generateStandalone(): InterceptionCertificateStatus {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        val now = Date()
        val expiry = Calendar.getInstance().apply {
            time = now
            add(Calendar.YEAR, 10)
        }.time
        val principal = X500Principal("CN=RootTools Local Inspection CA,O=RootTools")
        val serial = BigInteger(63, SecureRandom()).max(BigInteger.ONE)
        val algorithmIdentifier = Der.sequence(Der.oidSha256WithRsa(), Der.nullValue())
        val toBeSigned = Der.sequence(
            Der.context0(Der.integer(BigInteger.valueOf(2))),
            Der.integer(serial),
            algorithmIdentifier,
            principal.encoded,
            Der.sequence(Der.utcTime(now), Der.utcTime(expiry)),
            principal.encoded,
            pair.public.encoded,
            Der.context3(
                Der.sequence(
                    Der.sequence(
                        Der.oid("2.5.29.19"),
                        Der.boolean(true),
                        Der.octetString(Der.sequence(Der.boolean(true))),
                    ),
                    Der.sequence(
                        Der.oid("2.5.29.15"),
                        Der.boolean(true),
                        Der.octetString(Der.bitString(byteArrayOf(0x06), 1)),
                    ),
                ),
            ),
        )
        val signer = Signature.getInstance("SHA256withRSA").apply {
            initSign(pair.private)
            update(toBeSigned)
        }
        val certificate = parseCertificate(
            Der.sequence(toBeSigned, algorithmIdentifier, Der.bitString(signer.sign(), 0)),
        )
        certificate.verify(pair.public)
        persist(certificate, CertificateSource.STANDALONE, pair.private.encoded)
        return status()
    }

    suspend fun installSystemModule(): Result<InterceptionCertificateStatus> = runCatching {
        val certificate = loadCertificate() ?: error("Import or generate a CA first")
        val hash = subjectHashOld(certificate)
        val staging = File(exportedCertificate.parentFile, "$hash.0").apply {
            parentFile?.mkdirs()
            writeText(toPem(certificate))
        }
        val commands = CertificateCommandPolicy.commands(hash, staging.absolutePath)
            ?: error("Certificate module input failed validation")
        val result = shell.execute(commands.install, timeoutSeconds = MODULE_TIMEOUT_SECONDS)
        auditSink?.record(
            InterceptionAuditRecord(
                action = "install_ca_module",
                target = hash,
                after = "staged=${result.success}",
                success = result.success,
                rollbackHint = "Remove the RootTools Network Inspection CA module and reboot",
            ),
        )
        check(result.success) { result.output.ifBlank { "Root certificate module install failed" } }
        status()
    }

    suspend fun removeSystemModule(): Result<Unit> = runCatching {
        val result = shell.execute(
            CertificateCommandPolicy.removeModules(),
            timeoutSeconds = MODULE_TIMEOUT_SECONDS,
        )
        auditSink?.record(
            InterceptionAuditRecord(
                action = "remove_ca_module",
                success = result.success,
                rollbackHint = "Reinstall the same verified CA module if system trust is still required",
            ),
        )
        check(result.success) { result.output.ifBlank { "Root certificate module removal failed" } }
    }

    fun fingerprintOfPem(pem: String): String = sha256Fingerprint(parseCertificate(pem).encoded)

    private fun persist(
        certificate: X509Certificate,
        source: CertificateSource,
        privateKey: ByteArray?,
    ) {
        val pem = toPem(certificate)
        certificateFile.writeText(pem)
        if (privateKey == null) privateKeyFile.delete() else privateKeyFile.writeBytes(privateKey)
        sourceFile.writeText(source.wireName)
        exportedCertificate.parentFile?.mkdirs()
        exportedCertificate.writeText(pem)
    }

    private fun loadCertificate(): X509Certificate? = runCatching {
        if (!certificateFile.exists()) return null
        parseCertificate(certificateFile.readBytes())
    }.getOrNull()

    private fun parseCertificate(value: String): X509Certificate = parseCertificate(value.byteInputStream())

    private fun parseCertificate(value: ByteArray): X509Certificate = parseCertificate(value.inputStream())

    private fun parseCertificate(input: java.io.InputStream): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(input) as X509Certificate

    private fun subjectHashOld(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("MD5").digest(certificate.subjectX500Principal.encoded)
        return (3 downTo 0).joinToString("") { "%02x".format(digest[it].toInt() and 0xff) }
    }

    private fun sha256Fingerprint(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(":") { "%02X".format(it.toInt() and 0xff) }

    private fun toPem(certificate: X509Certificate): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(certificate.encoded)
        return "-----BEGIN CERTIFICATE-----\n$body\n-----END CERTIFICATE-----\n"
    }

    private companion object {
        const val MAX_PEM_LENGTH = 128 * 1024
        const val STATUS_TIMEOUT_SECONDS = 3L
        const val MODULE_TIMEOUT_SECONDS = 10L
    }
}

private object Der {
    fun sequence(vararg values: ByteArray) = tlv(0x30, values.fold(ByteArray(0)) { result, item -> result + item })
    fun context0(value: ByteArray) = tlv(0xA0, value)
    fun context3(value: ByteArray) = tlv(0xA3, value)
    fun integer(value: BigInteger) = tlv(0x02, value.toByteArray())
    fun boolean(value: Boolean) = tlv(0x01, byteArrayOf(if (value) 0xFF.toByte() else 0))
    fun nullValue() = byteArrayOf(0x05, 0x00)
    fun octetString(value: ByteArray) = tlv(0x04, value)
    fun bitString(value: ByteArray, unusedBits: Int) = tlv(0x03, byteArrayOf(unusedBits.toByte()) + value)
    fun oidSha256WithRsa() = oid("1.2.840.113549.1.1.11")

    fun utcTime(date: Date): ByteArray {
        val formatter = java.text.SimpleDateFormat("yyMMddHHmmss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        return tlv(0x17, formatter.format(date).toByteArray(Charsets.US_ASCII))
    }

    fun oid(value: String): ByteArray {
        val parts = value.split('.').map(String::toLong)
        val output = ByteArrayOutputStream()
        output.write((parts[0] * 40 + parts[1]).toInt())
        parts.drop(2).forEach { part ->
            var remaining = part
            val stack = mutableListOf((remaining and 0x7F).toInt())
            remaining = remaining ushr 7
            while (remaining > 0) {
                stack += (remaining and 0x7F).toInt() or 0x80
                remaining = remaining ushr 7
            }
            stack.asReversed().forEach(output::write)
        }
        return tlv(0x06, output.toByteArray())
    }

    private fun tlv(tag: Int, value: ByteArray) = byteArrayOf(tag.toByte()) + length(value.size) + value

    private fun length(size: Int): ByteArray = when {
        size < 128 -> byteArrayOf(size.toByte())
        size <= 0xFF -> byteArrayOf(0x81.toByte(), size.toByte())
        else -> byteArrayOf(0x82.toByte(), (size ushr 8).toByte(), size.toByte())
    }
}
