package no.nav.emottak.payload.helseid.util.security

import jakarta.xml.bind.DatatypeConverter
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Object
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1TaggedObject
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.DLSequence
import org.bouncycastle.asn1.x500.RDN
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x500.style.IETFUtils
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.DistributionPointName
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.jce.provider.JCEECPublicKey
import org.bouncycastle.jce.spec.ECParameterSpec
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.Principal
import java.security.PublicKey
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date
import javax.security.auth.x500.X500Principal
import org.slf4j.LoggerFactory

@Suppress("TooManyFunctions")
object X509Utils {
    val ACCESS_IDENTIFIER_OCSP = ASN1ObjectIdentifier("1.3.6.1.5.5.7.48.1")
    val ACCESS_IDENTIFIER_CRL = ASN1ObjectIdentifier("1.3.6.1.5.5.7.48.2")
    private val AIA_METHODS = listOf(ACCESS_IDENTIFIER_OCSP, ACCESS_IDENTIFIER_CRL)
    private const val FAILED_TO_GET_POLICY_ID = "failed to get policy id"
    private const val SHA256 = "SHA-256"
    private val BC_STYLE_REGEX_MAP = listOf(BCStyle.OU to Regex("^.*- *(\\d{9})\$|^.*-(\\d{9})-.*\$"),
        BCStyle.SERIALNUMBER to Regex("^(\\d{9})\$"),
        BCStyle.ORGANIZATION_IDENTIFIER to Regex(".+-(\\d{9})\$"),
        BCStyle.O to Regex(".+- *(\\d{9})\$"))
    private val LOG = LoggerFactory.getLogger("no.nav.emottak.payload.helseid.util.security.X509Utils")
    private const val DASHES = "-----"
    const val BEGIN_CERT = "-----BEGIN CERTIFICATE-----\n"
    const val END_CERT = "-----END CERTIFICATE-----\n"

    /**
     * loads a certificate from a string
     * @param string the encoded certificate
     * @return the certificate
     */
    fun loadCertificate(string: String): X509Certificate {
        val bytes = if (string.startsWith(DASHES)) {
            string.toByteArray()
        } else {
            "$BEGIN_CERT$string$END_CERT".toByteArray()
        }
        return loadCertificate(bytes)
    }

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
     * checks if a certificate has a given key usage
     * @param certificate The certificate.
     * @param keyUsage The key usage.
     * @return true or false.
     */
    fun hasKeyUsage(certificate: X509Certificate, keyUsage: KeyUsage): Boolean {
        val usage = certificate.keyUsage
        return usage != null && usage[keyUsage.ordinal]
    }

    /**
     * Checks if a certificate has a given extended key usage
     * @param certificate The certificate.
     * @param purposeId The key purpose.
     * @return true or false.
     */
    fun hasExtendedKeyUsage(certificate: X509Certificate, purposeId: KeyPurposeId): Boolean =
        try {
            certificate.extendedKeyUsage?.contains(purposeId.id) ?: false
        } catch (e: CertificateException) {
            throw SecurityException("Failed to find extended key usage", e)
        }

    /**
     * gets the key usage as a string
     * @param certificate the certificate
     * @return the key usage
     */
    @Suppress("NestedBlockDepth")
    fun getKeyUsage(certificate: X509Certificate): String {
        val usage = certificate.keyUsage
        val sb = StringBuilder()
        if (usage.isNotEmpty()) {
            KeyUsage.entries.forEach {
                if (usage[it.ordinal]) {
                    if (sb.isNotEmpty()) {
                        sb.append(", ")
                    }
                    sb.append(it.name)
                }
            }
        }
        return sb.toString()
    }

    /**
     * checks if a certificate is self-signed
     * @param certificate the certificate
     * @return true if self signed
     */
    @Suppress("SwallowedException")
    fun isSelfSigned(certificate: X509Certificate): Boolean =
        if (certificate.subjectX500Principal == certificate.issuerX500Principal) {
            try {
                certificate.verify(certificate.publicKey)
                true
            } catch (e: GeneralSecurityException) {
                false
            }
        } else {
            false
        }

    /**
     * checks if the subject may act as a ca
     * @param certificate the certificate
     * @return true if ca
     */
    fun isCA(certificate: X509Certificate): Boolean = certificate.basicConstraints != -1

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
     * gets the encoded certificate.
     * @param certificate the certificate.
     * @return the encode certificate.
     */
    @Suppress("SwallowedException")
    fun getEncoded(certificate: X509Certificate): ByteArray =
        try {
            certificate.encoded
        } catch (e: CertificateEncodingException) {
            throw SecurityException("failed to get encoded certificate")
        }

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
     * Checks if the certificate has a certain certificate policy.
     * The list of policies can be regular expressions.
     * This method ignores all exceptions and returns false if it fails.
     * @param certificate The certificate.
     * @param policies The oids of the policies.
     * @return true if the certificate has one of the policies
     */
    fun hasACertificatePolicy(certificate: X509Certificate, policies: Collection<String>): Boolean {
        try {
            val certificatePolicies = getCertificatePolicies(certificate)
            // we first try an exact match, then regular expression matching
            return hasACertificatePolicy(certificatePolicies, policies) ||
                    hasACertificatePolicyMatchingRegularExpression(certificatePolicies, policies)
        } catch (e: IOException) {
            LOG.error(FAILED_TO_GET_POLICY_ID, e)
        }
        return false
    }

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
     * gets the first crl distribution points with http scheme
     * @param certificate the certificate
     * @return the crl distribution points
     */
    fun getCrlDistributionPoint(certificate: X509Certificate): String =
        getCrlDistributionPoints(certificate, "http").firstOrNull().orEmpty()

    /**
     * gets all crl distribution points with a certain scheme
     * @param certificate the certificate
     * @param scheme the scheme
     * @return list of crl distribution points
     */
    fun getCrlDistributionPoints(certificate: X509Certificate, scheme: String): List<String> =
        getCrlDistributionPoints(certificate).filter { it.startsWith(scheme) }

    /**
     * gets all crl distribution points
     * @param certificate the certificate
     * @return list of crl distribution points
     */
    @Suppress("NestedBlockDepth")
    fun getCrlDistributionPoints(certificate: X509Certificate): List<String> {
        val list: MutableList<String> = ArrayList()
        val extVal = certificate.getExtensionValue(Extension.cRLDistributionPoints.id)
        if (extVal != null) {
            val crlDistPoint = CRLDistPoint.getInstance(JcaX509ExtensionUtils.parseExtensionValue(extVal))
            val points = crlDistPoint.distributionPoints
            for (p in points) {
                val dpn = p.distributionPoint
                if (dpn.type == DistributionPointName.FULL_NAME) {
                    addCrlDistributionPoint(list, dpn)
                }
            }
        }
        return list
    }

    private fun addCrlDistributionPoint(list: MutableList<String>, dpn: DistributionPointName) {
        val names = GeneralNames.getInstance(dpn.name).names
        for (gn in names) {
            if (gn.tagNo == GeneralName.uniformResourceIdentifier) {
                list.add((gn.name as DERIA5String).string)
            }
        }
    }

    /**
     * Checks if a list of certificate policies matches any in a list of regular expressions.
     * @param certificatePolicies The list of certificate policies.
     * @param regularExpressions List of regular expressions that we want to check.
     * @return true if the list of certificate policies has a policy that matches one of the regular expressions.
     */
    private fun hasACertificatePolicyMatchingRegularExpression(
        certificatePolicies: Collection<String>,
        regularExpressions: Collection<String>): Boolean {
        val re = regularExpressions.map { Regex(it) }
        return certificatePolicies.any {
            re.any { r -> it.matches(r) }
        }
    }

    /**
     * Checks if a list of certificate policies matches any in a list of certificate policies.
     * @param certificatePolicies The list of certificate policies.
     * @param policies List of policies that we want to check.
     * @return true if the list of certificate policies has a policy that equals one of the policies.
     */
    private fun hasACertificatePolicy(certificatePolicies: Collection<String>, policies: Collection<String>): Boolean {
        return certificatePolicies.any {
            policies.any { p -> it == p }
        }
    }

    /**
     * gets the certificate policies
     * @param certificate the certificate
     * @return list of policies
     * @throws IOException if failure
     */
    fun getCertificatePolicies(certificate: X509Certificate): Collection<String> {
        val bytes = certificate.getExtensionValue(Extension.certificatePolicies.id)
        return if (bytes != null) {
            val policies = JcaX509ExtensionUtils.parseExtensionValue(bytes) as DLSequence
            (0 until policies.size()).map { policies.getObjectAt(it).toString().replace("[\\[\\]]".toRegex(), "") }
        } else {
            emptyList()
        }
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


    /**
     * gets principals name
     * @param principal the principal
     * @return the name
     */
    fun getPrincipalName(principal: Principal?): String {
        if (principal != null) {
            return if (principal is X500Principal) {
                val name = X500Name.getInstance(principal.encoded)
                val comp1: Comparator<RDN> = compareBy { it.first.type.id }
                val comp: Comparator<RDN> = comp1.thenComparing( compareBy { it.first.value.toString() })
                val sortedRdn: Array<out RDN> = name.rdNs.sortedArrayWith(comp)
                val sortedName = X500Name(sortedRdn)
                sortedName.toString()
            } else {
                principal.name
            }
        }
        throw IllegalArgumentException("missing principal")
    }

    /**
     * gets the AuthorityInfoAccess from a certificate with CRL access
     * @param certificate the certificate
     * @return the CRL distribution point
     */
    fun getAuthorityInfoAccess(certificate: X509Certificate): String =
        getAuthorityInfoAccess(certificate, ACCESS_IDENTIFIER_CRL)

    /**
     * gets the AuthorityInfoAccess from a certificate for a given access method
     * @param certificate the certificate
     * @param method the method
     * @return the authority info access
     */
    fun getAuthorityInfoAccess(certificate: X509Certificate, method: ASN1ObjectIdentifier): String {
        val list = getAuthorityInfoAccessList(certificate, method)
        return if (list.isEmpty()) "" else list[0]
    }

    /**
     * gets the AuthorityInfoAccess from a certificate for a given access method with a specific scheme
     * @param certificate the certificate
     * @param scheme the scheme
     * @return the authority info access
     */
    fun getAuthorityInfoAccess(certificate: X509Certificate, scheme: String): String =
        getAuthorityInfoAccessList(certificate, ACCESS_IDENTIFIER_CRL).firstOrNull { it.startsWith(scheme) }.orEmpty()

    /**
     * gets the AuthorityInfoAccess from a certificate for CRL and OCSP schemas
     * @param certificate the certificate
     * @return list of authority info access
     */
    fun getAuthorityInfoAccessList(certificate: X509Certificate): Collection<String> =
        AIA_METHODS.map { getAuthorityInfoAccessList(certificate, it) }.flatten()

        /**
     * gets all the AuthorityInfoAccess from a certificate for a given access method
     * @param certificate the certificate
     * @param method the method
     * @return the authority info access
     */
    @Suppress("NestedBlockDepth")
    fun getAuthorityInfoAccessList(certificate: X509Certificate, method: ASN1ObjectIdentifier): List<String> {
        val list: MutableList<String> = ArrayList()
        val obj = getExtension(certificate, Extension.authorityInfoAccess.id)
        if (obj != null) {
            val accessDescriptions = obj as ASN1Sequence
            for (i in 0 until accessDescriptions.size()) {
                val accessDescription = accessDescriptions.getObjectAt(i) as ASN1Sequence
                if (accessDescription.size() == 2) {
                    val identifier = accessDescription.getObjectAt(0) as ASN1ObjectIdentifier
                    if (method.equals(identifier)) {
                        list.add(getStringFromGeneralName(accessDescription.getObjectAt(1) as ASN1Object))
                    }
                }
            }
        }
        return list
    }

    /**
     * gets a string from a general name. helper class for
     * getAuthorityInformationAccess
     */
    private fun getStringFromGeneralName(names: ASN1Object): String {
        val taggedObject = names as ASN1TaggedObject
        return String(ASN1OctetString.getInstance(taggedObject, false).octets)
    }

    /**
     * gets the extension value from a certificate
     * @param certificate the certificate
     * @param oid the oid
     * @return the extension value value
     * @throws IOException when error occurs
     */
    fun getExtension(certificate: X509Certificate, oid: String?): ASN1Object? {
        val value = certificate.getExtensionValue(oid)
        return if (value == null) {
            null
        } else {
            JcaX509ExtensionUtils.parseExtensionValue(value)
        }
    }

    /**
     * gets the crl number of a crl
     * @param crl the crl
     * @return the crl number or -1 if not found
     */
    fun getCRLNumber(crl: X509CRL): Int {
        val bytes = crl.getExtensionValue(Extension.cRLNumber.id)
        val o: ASN1Object = JcaX509ExtensionUtils.parseExtensionValue(bytes)
        if (o is ASN1Integer) {
            return o.value.toInt()
        }
        return -1
    }

    /**
     * Gets the public key algorithm (and key size)
     * @param publicKey the public key
     * @return the algorith and keysize
     */
    fun getPublicKeyAlgorithm(publicKey: PublicKey) = "${publicKey.algorithm}-${keyLength(publicKey)}"

    /**
     * Gets the key length of supported keys
     * @param pk PublicKey used to derive the keysize
     * @return -1 if key is unsupported, otherwise a number >= 0. 0 usually means the length can not be calculated,
     * for example if the key is an EC key and the "implicitlyCA" encoding is used.
     */
    private fun keyLength(pk: PublicKey): Int =
        when (pk) {
            is RSAPublicKey -> pk.modulus.bitLength()
            is JCEECPublicKey -> {
                val spec: ECParameterSpec = pk.parameters
                spec.n?.bitLength() ?: // We support the key, but we don't know the key length
                0
            }
            is ECPublicKey -> {
                val spec: java.security.spec.ECParameterSpec = pk.params
                spec.order?.bitLength() // does this really return something we expect?
                    ?: // We support the key, but we don't know the key length
                    0
            }
            is DSAPublicKey -> if (pk.params != null) pk.params.p.bitLength() else pk.y.bitLength()
            else -> -1
        }

}
