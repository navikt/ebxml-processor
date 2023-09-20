package no.nav.emottak.util

import io.ktor.server.plugins.BadRequestException
import java.io.ByteArrayInputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

internal fun isSelfSigned(certificate: X509Certificate) =
    certificate.subjectX500Principal == certificate.issuerX500Principal

internal fun createX509Certificate(byteArray: ByteArray): X509Certificate {
    val cf = CertificateFactory.getInstance("X.509")
    return try {
        cf.generateCertificate(ByteArrayInputStream(byteArray)) as X509Certificate
    }
    catch (e: CertificateException) {
        throw BadRequestException("")
    }
}