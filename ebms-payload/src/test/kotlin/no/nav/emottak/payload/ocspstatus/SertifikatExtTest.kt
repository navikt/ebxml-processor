package no.nav.emottak.payload.ocspstatus

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.CertificatePolicies
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.PolicyInformation
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * These tests target the certificate-policy-based classification used to decide whether
 * FNR (SSN) validation may be skipped for OCSP checks (see OcspStatusService.getOCSPStatus).
 * A wrong classification here would either bypass a security check for a personal certificate,
 * or wrongly demand an FNR from a legitimate business certificate.
 */
class SertifikatExtTest {

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    @Test
    fun `isVirksomhetssertifikat returns true for a known virksomhet policy OID`() {
        val cert = certificateWithPolicy("O=Test Org", "2.16.578.1.26.1.0.9.9")
        assertTrue(cert.isVirksomhetssertifikat())
    }

    @Test
    fun `isVirksomhetssertifikat returns false for a policy OID not in the virksomhet list`() {
        val cert = certificateWithPolicy("O=Test Org", "1.2.3.4.5.6.7.8.9")
        assertFalse(cert.isVirksomhetssertifikat())
    }

    @Test
    fun `isVirksomhetssertifikat returns false when certificate has no certificate policies extension`() {
        val cert = certificateWithoutPolicy("O=Test Org")
        assertFalse(cert.isVirksomhetssertifikat())
    }

    @Test
    fun `getOrganizationNumber returns null for a personal certificate even if the DN looks like an org number`() {
        val cert = certificateWithPolicy("O=123456789", "1.2.3.4.5.6.7.8.9")
        assertEquals(null, cert.getOrganizationNumber())
    }

    @Test
    fun `getOrganizationNumber extracts the org number from a virksomhet certificate`() {
        val cert = certificateWithPolicy("O=123456789", "2.16.578.1.26.1.0.9.9")
        assertEquals("123456789", cert.getOrganizationNumber())
    }

    @Test
    fun `getOrganizationNumber returns empty string for a virksomhet certificate without a matching org number field`() {
        val cert = certificateWithPolicy("O=Some Company Name", "2.16.578.1.26.1.0.9.9")
        assertEquals("", cert.getOrganizationNumber())
    }

    private fun certificateWithPolicy(subjectDn: String, policyOid: String): X509Certificate {
        return buildSelfSignedCertificate(subjectDn) { builder ->
            val policies = CertificatePolicies(arrayOf(PolicyInformation(ASN1ObjectIdentifier(policyOid))))
            builder.addExtension(Extension.certificatePolicies, false, policies)
        }
    }

    private fun certificateWithoutPolicy(subjectDn: String): X509Certificate {
        return buildSelfSignedCertificate(subjectDn) { }
    }

    private fun buildSelfSignedCertificate(subjectDn: String, customize: (JcaX509v3CertificateBuilder) -> Unit): X509Certificate {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val subject = X500Name(subjectDn)
        val now = Date()
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(System.currentTimeMillis()),
            now,
            Date(now.time + 3_600_000),
            subject,
            keyPair.public
        )
        customize(builder)
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        return JcaX509CertificateConverter().setProvider(BouncyCastleProvider()).getCertificate(builder.build(signer))
    }
}
