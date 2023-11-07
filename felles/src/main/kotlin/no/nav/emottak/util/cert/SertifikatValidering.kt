package no.nav.emottak.util.cert

import no.nav.emottak.util.crypto.getIntermediateCerts
import no.nav.emottak.util.crypto.getTrustedRootCerts
import no.nav.emottak.util.isSelfSigned
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.slf4j.LoggerFactory
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

internal val log = LoggerFactory.getLogger("no.nav.emottak.cpa.validation.SertifikatValidering")
class SertifikatValidering(
    val crlChecker: CRLChecker,
    val trustedRootCerts: Set<X509Certificate> = getTrustedRootCerts(),
    val intermediateCerts: Set<X509Certificate> = getIntermediateCerts()
) {
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
        val trustAnchors = trustedRootCerts.map {
            TrustAnchor(it, null)
        }.toSet()

        val pkixParams = PKIXBuilderParameters(trustAnchors, selector)
        pkixParams.isRevocationEnabled = false
        pkixParams.date = Date.from(Instant.now())

        val intermediateCertStore = CertStore.getInstance("Collection", CollectionCertStoreParameters(intermediateCerts), "BC")
        pkixParams.addCertStore(intermediateCertStore)

        val builder = CertPathBuilder.getInstance("PKIX", "BC")
        try {
            builder.build(pkixParams) as PKIXCertPathBuilderResult
        } catch (e: CertPathBuilderException) {
            throw CertificateValidationException("Sertifikatvalidering feilet", e)
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
            val crlDistributionPoints = CRLDistPoint.getInstance(JcaX509ExtensionUtils.parseExtensionValue(crlDistributionPoint))
            log.warn("CRL for $crlDistributionPoints feilet")
            throw e
        }
    }

}

