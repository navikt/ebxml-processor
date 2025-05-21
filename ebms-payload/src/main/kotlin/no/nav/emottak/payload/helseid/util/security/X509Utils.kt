package no.nav.emottak.payload.helseid.util.security

import jakarta.xml.bind.DatatypeConverter
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

@Suppress("TooManyFunctions")
object X509Utils {
    private const val SHA256 = "SHA-256"

    /**
     * gets the issuer's distinguished name
     * @param certificate the certificate
     * @return the issuer name
     */
    fun getIssuerDN(certificate: X509Certificate): String = getName(certificate.issuerX500Principal)

    /**
     * gets the subject's distinguished name
     * @param certificate the certificate
     * @return the subject name
     */
    fun getSubjectDN(certificate: X509Certificate): String = getName(certificate.subjectX500Principal)

    /**
     * Stringifies a certificate.
     * @param certificate The certificate to stringify.
     * @return The string.
     */
    fun toString(certificate: X509Certificate): String =
        getSubjectDN(certificate) + " issued by " +
                getIssuerDN(certificate) + " with serial number " +
                certificate.serialNumber

    /**
     * Gets a certificate's thumbprint as a hex string.
     * @param certificate The certificate.
     * @param algorithm the algorithm, default SHA-256.
     * @return The thumbprint as a hex string
     */
    fun thumbprintHex(certificate: X509Certificate, algorithm: String = SHA256): String =
        DatatypeConverter.printHexBinary(thumbprint(certificate, algorithm)).lowercase()

    /**
     * Gets a certificate's thumbprint as a byte array.
     * @param certificate The certificate.
     * @param algorithm the algorithm, default SHA-256.
     * @return the raw thumbprint as a byte array.
     */
    // Hashing data is security-sensitive
    fun thumbprint(certificate: X509Certificate, algorithm: String = SHA256): ByteArray =
        try {
            val md = MessageDigest.getInstance(algorithm)
            md.digest(certificate.encoded)
        } catch (e: NoSuchAlgorithmException) {
            throw SecurityException("failed to get certificate's thumbprint", e)
        } catch (e: CertificateEncodingException) {
            throw SecurityException("failed to get certificate's thumbprint", e)
        }

    /**
     * gets principals name
     * @param principal the principal
     * @return the name
     */
    private fun getName(principal: X500Principal): String = principal.getName(X500Principal.RFC1779)
        .replace("OID.2.5.4.5", "SERIALNUMBER")
        .replace("OID.2.5.4.97", "organizationIdentifier")
}
