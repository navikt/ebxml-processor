package no.nav.emottak.util

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun isSelfSigned(certificate: X509Certificate) =
    certificate.subjectX500Principal == certificate.issuerX500Principal

private val provider = BouncyCastleProvider()
fun createX509Certificate(byteArray: ByteArray): X509Certificate {
    val cf = CertificateFactory.getInstance("X.509", provider)
    return try {
        cf.generateCertificate(ByteArrayInputStream(byteArray)) as X509Certificate
    }
    catch (e: CertificateException) {
        throw RuntimeException("Kunne ikke opprette X509Certificate fra ByteArray", e)
    }
}

fun createCRLFile(byteArray: ByteArray): X509CRL {
    val factory = CertificateFactory.getInstance("X.509", provider)
    return factory.generateCRL(ByteArrayInputStream(byteArray)) as X509CRL
}

@OptIn(ExperimentalEncodingApi::class)
 fun decodeBase64(base64String: ByteArray): ByteArray = java.util.Base64.getMimeDecoder().decode(base64String)