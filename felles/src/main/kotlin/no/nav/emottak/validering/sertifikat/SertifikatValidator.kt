package no.nav.emottak.validering.sertifikat

import no.nav.emottak.crypto.KeyStoreManager
import no.nav.emottak.crypto.trustStoreConfig
import no.nav.emottak.util.isSelfSigned
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import java.security.Provider
import java.security.cert.CertPathBuilder
import java.security.cert.CertPathBuilderException
import java.security.cert.CertStore
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.CollectionCertStoreParameters
import java.security.cert.PKIXBuilderParameters
import java.security.cert.PKIXCertPathBuilderResult
import java.security.cert.TrustAnchor
import java.security.cert.X509CertSelector
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Date

class SertifikatValidator(
    private val crlChecker: CRLChecker,
    trustStore: KeyStoreManager = KeyStoreManager(trustStoreConfig()),
    private val provider: Provider = BouncyCastleProvider()
) {
    private val logger = LoggerFactory.getLogger(SertifikatValidator::class.java)
    private val trustedRootCertificates: Set<X509Certificate> = trustStore.getTrustedRootCerts()
    private val intermediateCertificates: Set<X509Certificate> = trustStore.getIntermediateCerts()

    fun validateCertificate(certificate: X509Certificate) {
        if (isSelfSigned(certificate)) {
            throw CertificateValidationException("Sertifikat er selvsignert")
        }
        sjekkGyldigTidspunkt(certificate, Instant.now())
        sjekkSertifikatMotTrustedCa(certificate)
        sjekkCRL(certificate)
    }

    fun sjekkSertifikatMotTrustedCa(certificate: X509Certificate) {
        val selector = X509CertSelector()
        selector.certificate = certificate
        val trustAnchors = trustedRootCertificates.map {
            TrustAnchor(it, null)
        }.toSet()

        val pkixParams = PKIXBuilderParameters(trustAnchors, selector)
        pkixParams.isRevocationEnabled = false
        pkixParams.date = Date.from(Instant.now())

        val intermediateCertStore =
            CertStore.getInstance("Collection", CollectionCertStoreParameters(intermediateCertificates), provider)
        pkixParams.addCertStore(intermediateCertStore)

        val builder = CertPathBuilder.getInstance("PKIX", provider)
        try {
            builder.build(pkixParams) as PKIXCertPathBuilderResult
        } catch (e: CertPathBuilderException) {
            logger.warn("Sertifikatvalidering feilet <${certificate.serialNumber.toString(16)}> <${certificate.subjectX500Principal.name}> utstedt av <${certificate.issuerX500Principal.name}>", e)
            throw CertificateValidationException("Sertifikatvalidering feilet for sertifikat utstedt av <${certificate.issuerX500Principal.name}>", e)
        }
    }

    fun sjekkGyldigTidspunkt(certificate: X509Certificate, instant: Instant) {
        try {
            certificate.checkValidity(Date(instant.toEpochMilli()))
        } catch (e: CertificateExpiredException) {
            throw CertificateValidationException("Sertifikat utløpt <${e.localizedMessage}>", e)
        } catch (e: CertificateNotYetValidException) {
            throw CertificateValidationException("Sertifikat ikke gyldig enda <${e.localizedMessage}>", e)
        }
    }

    fun sjekkCRL(certificate: X509Certificate) {
        try {
            crlChecker.getCRLRevocationInfo(certificate.issuerX500Principal.name, certificate.serialNumber)
        } catch (e: CertificateValidationException) {
            throw e
        } catch (e: Exception) {
            val crlDistributionPoint = certificate.getExtensionValue(Extension.cRLDistributionPoints.toString())
            val crlDistributionPoints =
                CRLDistPoint.getInstance(JcaX509ExtensionUtils.parseExtensionValue(crlDistributionPoint))
            logger.warn("CRL for $crlDistributionPoints feilet")
            throw e
        }
    }
}
