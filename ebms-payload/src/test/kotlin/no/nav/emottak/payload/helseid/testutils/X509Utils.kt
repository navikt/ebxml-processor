package no.nav.emottak.payload.helseid.testutils

import jakarta.xml.bind.DatatypeConverter
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

@Suppress("TooManyFunctions")
object X509Utils {
    private const val SHA256 = "SHA-256"
    
    fun getIssuerDN(certificate: X509Certificate): String = getName(certificate.issuerX500Principal)

    fun getSubjectDN(certificate: X509Certificate): String = getName(certificate.subjectX500Principal)

    fun toString(certificate: X509Certificate): String =
        getSubjectDN(certificate) + " issued by " +
                getIssuerDN(certificate) + " with serial number " +
                certificate.serialNumber

    fun thumbprintHex(certificate: X509Certificate, algorithm: String = SHA256): String =
        DatatypeConverter.printHexBinary(thumbprint(certificate, algorithm)).lowercase()

    fun thumbprint(certificate: X509Certificate, algorithm: String = SHA256): ByteArray =
        try {
            val md = MessageDigest.getInstance(algorithm)
            md.digest(certificate.encoded)
        } catch (e: NoSuchAlgorithmException) {
            throw SecurityException("failed to get certificate's thumbprint", e)
        } catch (e: CertificateEncodingException) {
            throw SecurityException("failed to get certificate's thumbprint", e)
        }

    private fun getName(principal: X500Principal): String = principal.getName(X500Principal.RFC1779)
        .replace("OID.2.5.4.5", "SERIALNUMBER")
        .replace("OID.2.5.4.97", "organizationIdentifier")
}
