package no.nav.emottak.payload.helseid.util.security

import jakarta.xml.bind.DatatypeConverter
import org.bouncycastle.asn1.x500.RDN
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x500.style.IETFUtils
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date
import javax.security.auth.x500.X500Principal

@Suppress("TooManyFunctions")
object X509Utils {
    private const val SHA256 = "SHA-256"
    private val BC_STYLE_REGEX_MAP = listOf(
        BCStyle.OU to Regex("^.*- *(\\d{9})\$|^.*-(\\d{9})-.*\$"),
        BCStyle.SERIALNUMBER to Regex("^(\\d{9})\$"),
        BCStyle.ORGANIZATION_IDENTIFIER to Regex(".+-(\\d{9})\$"),
        BCStyle.O to Regex(".+- *(\\d{9})\$")
    )

    /**
     * loads a certificate from a byte array
     * @param bytes the encoded certificate
     * @return the certificate
     */
    fun loadCertificate(bytes: ByteArray): X509Certificate {
        return try {
            val certFactory = CertificateFactory.getInstance("X.509")
            ByteArrayInputStream(bytes).use { baos ->
                certFactory.generateCertificate(baos) as X509Certificate
            }
        } catch (e: CertificateException) {
            throw SecurityException("Failed to load certificate", e)
        }
    }

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
     * gets the subject's organization number
     * @param certificate the certificate
     * @return the organisation number
     */
    fun getOrganizationNumber(certificate: X509Certificate) = getOrganizationNumberFromDN(getSubjectDN(certificate))

    /**
     * gets the subject's organization number
     * @param dn the distinguished name
     * @return the organisation number
     */
    @Suppress("MagicNumber")
    fun getOrganizationNumberFromDN(dn: String): String {
        val name = X500Name(dn)
        return BC_STYLE_REGEX_MAP.firstNotNullOfOrNull { (style, re) ->
            val s = name.getRDNs(style)
            val values = values(s)
            values.firstNotNullOfOrNull { v ->
                val m = re.matchEntire(v)
                val g = m?.groupValues
                when {
                    g == null -> null
                    g.size == 2 -> g[1]
                    g.size == 3 && g[1] != "" -> g[1]
                    g.size == 3 && g[1] == "" -> g[2]
                    else -> null
                }
            }
        } ?: ""
    }

    private fun values(rdns: Array<RDN>): Collection<String> =
        if (rdns.isEmpty()) listOf("") else rdns.map { value(it) }

    private fun value(rdn: RDN) = IETFUtils.valueToString(rdn.first.value)

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
     * Gets a certificate's thumbprint as a base64 urlencoded string.
     * @param certificate The certificate.
     * @param algorithm the algorithm, default SHA-256.
     * @return The thumbprint as a base64 urlencoded string
     */
    fun thumbprintBase64Url(certificate: X509Certificate, algorithm: String = SHA256): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(thumbprint(certificate, algorithm))

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
     * Checks if a certificate is valid. Does no revocation check.
     * @param certificate The certificate to check.
     * @return true if still valid.
     */
    fun isValid(certificate: X509Certificate): Boolean {
        val now = Date()
        return now.after(certificate.notBefore) && now.before(certificate.notAfter)
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
