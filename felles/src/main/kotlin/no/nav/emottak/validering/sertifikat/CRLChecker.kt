package no.nav.emottak.validering.sertifikat

import org.bouncycastle.asn1.x500.X500Name
import java.math.BigInteger
import java.security.cert.X509CRL
import java.security.cert.X509CRLEntry

class CRLChecker(
    private val crlStore: CRLStore
) {
    fun getCRLRevocationInfo(issuer: String, serialNumber: BigInteger) {
        getRevokedCertificate(issuer = X500Name(issuer), serialNumber = serialNumber)?.let {
            throw CertificateValidationException("Sertifikat revokert: serienummer <$serialNumber> revokert med reason <${it.revocationReason}> at <${it.revocationDate}>")
        }
    }

    private fun getRevokedCertificate(issuer: X500Name, serialNumber: BigInteger): X509CRLEntry? {
        return getCRLFile(issuer).getRevokedCertificate(serialNumber)
    }

    private fun getCRLFile(issuer: X500Name): X509CRL {
        val crl = crlStore.get(issuer)
            ?: throw CertificateValidationException("Issuer $issuer ikke støttet. CRL liste må oppdateres med issuer om denne skal støttes")
        crl.validate()
        return crl.file!!
    }
}
