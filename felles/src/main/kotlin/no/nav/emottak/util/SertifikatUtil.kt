package no.nav.emottak.util

import java.io.ByteArrayInputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun isSelfSigned(certificate: X509Certificate) =
    certificate.subjectX500Principal == certificate.issuerX500Principal

fun createX509Certificate(byteArray: ByteArray): X509Certificate {
    val cf = CertificateFactory.getInstance("X.509")
    return try {
        cf.generateCertificate(ByteArrayInputStream(byteArray)) as X509Certificate
    }
    catch (e: CertificateException) {
        throw RuntimeException("Kunne ikke opprette X509Certificate fra ByteArray", e)
    }
}

@OptIn(ExperimentalEncodingApi::class)
fun decodeBase64(base64String: ByteArray): ByteArray = Base64.decode(base64String)