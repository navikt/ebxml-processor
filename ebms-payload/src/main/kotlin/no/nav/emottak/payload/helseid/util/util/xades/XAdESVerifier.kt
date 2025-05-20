package no.nav.emottak.payload.helseid.util.util.xades

import no.nav.emottak.payload.helseid.HelseIDValidator
import no.nav.emottak.payload.helseid.NinTokenValidator
import no.nav.emottak.payload.helseid.util.lang.ByteUtil
import no.nav.emottak.payload.helseid.util.lang.DateUtil
import no.nav.emottak.payload.helseid.util.security.KeyUsage
import no.nav.emottak.payload.helseid.util.security.SecurityUtils
import no.nav.emottak.payload.helseid.util.security.X509Utils
import no.nav.emottak.payload.helseid.util.util.DelegatingNamespaceContext
import no.nav.emottak.payload.helseid.util.util.XMLUtil
import no.nav.emottak.payload.helseid.util.util.XPathUtil
import no.nav.emottak.payload.helseid.util.util.ocsp.OCSPResponseValidator
import no.nav.emottak.payload.helseid.util.util.xmldsig.XMLDSIGVerifier
import org.apache.xml.security.utils.Constants
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.cert.ocsp.CertificateID
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.Principal
import java.security.cert.X509Certificate
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale
import javax.xml.namespace.NamespaceContext

@Suppress("TooManyFunctions", "MaxLineLength")
class XAdESVerifier(
    private val skoProperties: SkoProperties,
    keyStore: KeyStore,
    private val xmldsigVerifier: XMLDSIGVerifier? = null,
    private val ocspResponseValidator: OCSPResponseValidator? = null
) :
    NinTokenValidator by HelseIDValidator(
        skoProperties.helseIdIssuer,
        skoProperties.issuerOrganizationNumber,
        skoProperties.helseIdAllowedClockSkewInMs
    ) {

    private val trustedCertificateAuthorities: Map<Principal, X509Certificate> =
        SecurityUtils.getAliases(keyStore).associate {
            val certificate = keyStore.getCertificate(it.trim()) as X509Certificate
            certificate.subjectX500Principal to certificate
        }

    private enum class RequiredCertificateType {
        PERSONAL, ENTERPRISE, TIMESTAMP, PERSONAL_OR_ENTERPRISE
    }

    /**
     * Verifies an XAdES signature.
     * @param encoded The signed document which is base64 encoded.
     * @return True if signature validates.
     */
    fun verify(encoded: String): Boolean = verify(XMLUtil.createDocumentFromB64EncodedData(encoded))

    /**
     * Verifies an XAdES signature.
     * @param doc The signed document.
     * @return True if signature validates.
     */
    fun verify(doc: Document): Boolean {
        // Must mark the Id's ID attribute.
        XMLUtil.setIdAttributes(doc)

        // some basic checking of paths before we start the serious work
        validatePaths(doc)
        val parentSignatureValueId = XPathUtil.getValueAtPath(doc, namespaceContext, XPATH_PARENT_SIGNATURE_VALUE_ID)
        val timeStamp = validateAndGetTimeStamp(doc, parentSignatureValueId)
        val dateTimeStamp = DateUtil.toDate(timeStamp)
        verifyCounterSignatureReferences(doc, parentSignatureValueId)
        val certificatesUsedInSignatures: MutableSet<X509Certificate> = HashSet()
        val certificatesByPrincipal = getAllEncapsulatedCertificatesByPrincipal(doc)
        val helseIDtoken = getHelseIDTokenNodesFromDocument(doc, namespaceContext)
        if (helseIDtoken != null) {
            certificatesUsedInSignatures.add(
                validateCertificate(
                    validate(
                        helseIDtoken,
                        timeStamp = timeStamp,
                        certificates = certificatesByPrincipal.values
                    ),
                    dateTimeStamp,
                    KeyUsage.NON_REPUDIATION,
                    RequiredCertificateType.ENTERPRISE,
                    "HelseID"
                )
            )

            // validate signature over the document
            certificatesUsedInSignatures.add(
                validateCertificate(
                    xmldsigVerifier!!.verifyXML(doc),
                    dateTimeStamp,
                    KeyUsage.NON_REPUDIATION,
                    RequiredCertificateType.PERSONAL_OR_ENTERPRISE,
                    "Document"
                )
            )
        } else {
            // validate signature over the document
            certificatesUsedInSignatures.add(
                validateCertificate(
                    xmldsigVerifier!!.verifyXML(doc),
                    dateTimeStamp,
                    KeyUsage.NON_REPUDIATION,
                    RequiredCertificateType.PERSONAL,
                    "Document"
                )
            )
        }

        // validate the signature over the timestamp
        certificatesUsedInSignatures.add(
            validateCertificate(
                verifyXMLDSIG(doc, XPATH_SIGNATURE_TIMESTAMP),
                dateTimeStamp,
                KeyUsage.DIGITAL_SIGNATURE,
                RequiredCertificateType.TIMESTAMP,
                "Timestamp"
            )
        )

        // validate the counter signature
        certificatesUsedInSignatures.add(
            validateCertificate(
                verifyXMLDSIG(doc, XPATH_SIGNATURE_COUNTERSIGNATURE),
                dateTimeStamp,
                KeyUsage.NON_REPUDIATION,
                RequiredCertificateType.ENTERPRISE,
                "Countersignature"
            )
        )

        // validate the OCSP responses
        verifyOCSPResponses(doc, certificatesUsedInSignatures, certificatesByPrincipal, dateTimeStamp)
        return true
    }

    /**
     * Gets the ssn from the OSCP responses in the doucment that matches the certificate. Does no validation.
     * @param doc the document with the XAdES siganture
     * @param certificate the certificate for the OSCP response we want
     * @return the ssn
     */
    fun getSsn(doc: Document, certificate: X509Certificate?): String {
        return ocspResponseValidator!!.getSsn(getAllOCSPResponses(doc), certificate!!)!!
    }

    /**
     * Gets all encapsulated certificates in the XAdES signed document
     * @param encoded base64 encoded XAdES signed document
     * @return collection of certificates
     */
    fun getEncapsulatedCertificates(encoded: String) =
        getAllEncapsulatedCertificatesByPrincipal(XMLUtil.createDocumentFromB64EncodedData(encoded)).values

    /**
     * Gets the type of the certificate
     * @param certificate the certifiate
     * @return the certificate type
     */
    fun getCertificateType(certificate: X509Certificate): CertificateType =
        when {
            X509Utils.isCA(certificate) -> CertificateType.CA
            X509Utils.hasACertificatePolicy(certificate, skoProperties.personalPolicyIds) -> CertificateType.PERSONAL
            X509Utils.hasACertificatePolicy(certificate, skoProperties.enterprisePolicyIds) -> CertificateType.ENTERPRISE
            else -> CertificateType.UNKNOWN
        }

    @Suppress("ThrowsCount")
    private fun validateCertificate(
        certificate: X509Certificate,
        timeStamp: Date,
        keyUsage: KeyUsage,
        requiredCertificateType: RequiredCertificateType,
        debugMsg: String
    ): X509Certificate {
        LOG.debug("{} signed by: {}", { debugMsg }, { X509Utils.toString(certificate) })
        if (!isCertificateIssuedByTrustedIssuer(certificate)) {
            throw RuntimeException(THE_CERTIFICATE + X509Utils.toString(certificate) + " is issued by an untrusted issuer")
        }
        if (!X509Utils.hasKeyUsage(certificate, keyUsage)) {
            throw RuntimeException(THE_CERTIFICATE + X509Utils.toString(certificate) + " does not have the required key usage " + keyUsage)
        }
        if (requiredCertificateType == RequiredCertificateType.TIMESTAMP &&
            !X509Utils.hasExtendedKeyUsage(certificate, KeyPurposeId.id_kp_timeStamping)
        ) {
            throw RuntimeException(THE_CERTIFICATE + X509Utils.toString(certificate) + " does not have the required extended key usage ${KeyPurposeId.id_kp_timeStamping}")
        }
        val certificateType = getCertificateType(certificate)
        if (requiredCertificateType == RequiredCertificateType.ENTERPRISE && certificateType != CertificateType.ENTERPRISE) {
            throw RuntimeException(THE_CERTIFICATE + X509Utils.toString(certificate) + " does not have the required certificate policy identifying it as an enterprise certificate.")
        }
        if (requiredCertificateType == RequiredCertificateType.PERSONAL && certificateType != CertificateType.PERSONAL) {
            throw RuntimeException(THE_CERTIFICATE + X509Utils.toString(certificate) + " does not have the required certificate policy identifying it as a personal certificate.")
        }
        if (requiredCertificateType == RequiredCertificateType.PERSONAL_OR_ENTERPRISE && certificateType != CertificateType.PERSONAL && certificateType != CertificateType.ENTERPRISE) {
            throw RuntimeException(THE_CERTIFICATE + X509Utils.toString(certificate) + " does not have the required certificate policy identifying it as a personal or enterprise certificate.")
        }
        if (timeStamp.after(certificate.notAfter)) {
            throw RuntimeException(THE_CERTIFICATE + X509Utils.toString(certificate) + " have an expiry date before the timestamp creation time")
        }
        return certificate
    }

    private fun verifyXMLDSIG(doc: Document, xpath: String): X509Certificate {
        return xmldsigVerifier!!.verifyXML((XPathUtil.getNodeAtPath(doc, namespaceContext, xpath) as Element))
    }

    private fun verifyOCSPResponses(
        doc: Document,
        certificatesUsedInSignatures: MutableSet<X509Certificate>,
        certificatesByPrincipal: Map<Principal, X509Certificate>,
        timeStamp: Date
    ) {
        if (LOG.isDebugEnabled) {
            certificatesByPrincipal.values.forEach { c: X509Certificate ->
                LOG.debug(
                    "Certificate with serialnumber {} (0x{}) and subject {} used in signatures",
                    c.serialNumber,
                    c.serialNumber.toString(HEX_RADIX),
                    c.subjectX500Principal
                )
            }
        }

        // check the certificates
        val certificatesBySerialNumber = certificatesByPrincipal.values.groupBy { it.serialNumber }

        // get all the OCSP requests
        val values = getAllOCSPResponses(doc)
        for (value in values) {
            val certificate = verifyOneOcspResponse(value, timeStamp, certificatesBySerialNumber)
            if (certificate != null) {
                certificatesUsedInSignatures.remove(certificate)
            }
        }
        if (certificatesUsedInSignatures.isNotEmpty()) {
            LOG.warn("The following certificates does not have an OCSP response: {}", {
                certificatesUsedInSignatures.joinToString(separator = ",") { c: X509Certificate ->
                    String.format(
                        Locale.getDefault(),
                        "certificate with serial number %s (0x%s) and subject %s",
                        c.serialNumber,
                        c.serialNumber.toString(HEX_RADIX),
                        X509Utils.getSubjectDN(c)
                    )
                }
            })
            throw RuntimeException("Not all certificates has valid OCSP responses")
        }
    }

    /**
     * Gets all the OCSP responses contained in this XAdES siged document
     * @param doc The document
     * @return list of OCSP responses base64 encoded
     */
    private fun getAllOCSPResponses(doc: Document): List<String> {
        return XPathUtil.getNodeValuesAtPath(doc, namespaceContext, XPATH_ENCAPSULATED_OCSP_TEXT)
    }

    private fun validateAndGetTimeStamp(doc: Document, parentSignatureValueId: String): ZonedDateTime {
        val timeStampValue = XPathUtil.getValueAtPath(doc, namespaceContext, XPATH_TIMESTAMP_TEXT)
        val serialNumberValue = XPathUtil.getValueAtPath(doc, namespaceContext, XPATH_SERIALNUMBER_TEXT)
        if (timeStampValue.isBlank() || serialNumberValue.isBlank()) {
            throw RuntimeException("The element TstInfo of the XMLTimestampToken is invalid.")
        }
        verifyTimeStampReferences(doc, parentSignatureValueId)
        val claimedTime = ZonedDateTime.parse(timeStampValue)
        if (claimedTime.isAfter(ZonedDateTime.now())) {
            throw RuntimeException("Claimed time in timestamp is in the future.")
        }
        return claimedTime
    }

    private fun verifyTimeStampReferences(doc: Document, parentSignatureValueId: String) {
        val referencesXpath = XPATH_PROPERTIES_UNSIGNED_SIGNATURE +
            "/xades:SignatureTimeStamp/xades:XMLTimeStamp/dss:Timestamp/dsig:Signature/dsig:SignedInfo/dsig:Reference"
        if ( // check that one timestamp reference has the correct type
            !XPathUtil.exists(doc, namespaceContext, "$referencesXpath/@Type='$DSS_NS:XMLTimeStampToken'") ||
            // and that one reference references the parent signature
            !XPathUtil.exists(
                doc,
                namespaceContext,
                "$referencesXpath/attribute::*[translate(local-name(), 'URI','uri') = 'uri']='#$parentSignatureValueId'"
            )
        ) {
            throw RuntimeException("The XMLTimestampToken does not contain the correct signature references")
        }
    }

    private fun verifyCounterSignatureReferences(doc: Document, parentSignatureValueId: String) {
        val referencesXpath = XPATH_PROPERTIES_UNSIGNED_SIGNATURE +
            "/xades:CounterSignature/dsig:Signature/dsig:SignedInfo/dsig:Reference"

        // check that counter signature references all have correct type
        // because the type is inconsistent (casing) in the casing we do this in a complicated way
        val cnt = XPathUtil.countAtPath(doc, namespaceContext, "$referencesXpath/@Type") -
            XPathUtil.countAtPath(
                doc,
                namespaceContext,
                "$referencesXpath[@Type='http://uri.etsi.org/01903#CounterSignedSignature']"
            ) -
            XPathUtil.countAtPath(
                doc,
                namespaceContext,
                "$referencesXpath[@Type='http://uri.etsi.org/01903#CountersignedSignature']"
            )
        if (cnt > 0) {
            throw RuntimeException("All CounterSignatures residing in the XAdES QualifyingProperties must be of type 'http://uri.etsi.org/01903#CounterSignedSignature'")
        }

        // check that one of them references the parent signature
        if (!XPathUtil.exists(
                doc,
                namespaceContext,
                "$referencesXpath/attribute::*[translate(local-name(), 'URI','uri') = 'uri']='#$parentSignatureValueId'"
            )
        ) {
            throw RuntimeException("All CounterSignatures residing in the XAdES QualifyingProperties must qualify the original signature and be of type 'http://uri.etsi.org/01903#CounterSignedSignature'")
        }
    }

    /**
     * Verifies one OCSP response and returns the certificate that was verified in this response.
     * @param ocspResponse The base 64 encoded OCSP response.
     * @param timeStamp The timestamp in XADES signed document.
     * @param certificatesBySerialNumber The certificates in the XADES signed grouped by serial number.
     * @return the certificate that was verified
     */
    private fun verifyOneOcspResponse(
        ocspResponse: String,
        timeStamp: Date,
        certificatesBySerialNumber: Map<BigInteger, Collection<X509Certificate>>
    ): X509Certificate? {
        val certificateID = ocspResponseValidator!!.validate(ByteUtil.decodeBase64(ocspResponse), timeStamp)
        return findMatchingCertificate(certificateID, certificatesBySerialNumber)
    }

    private fun findMatchingCertificate(
        certificateID: CertificateID,
        certificatesBySerialNumber: Map<BigInteger, Collection<X509Certificate>>
    ): X509Certificate? {
        certificatesBySerialNumber[certificateID.serialNumber]?.forEach { certificate ->
            for (caCertificate in trustedCertificateAuthorities.values) {
                if (certificate.issuerX500Principal == caCertificate.subjectX500Principal && ocspResponseValidator!!.matchesIssuer(
                        certificateID,
                        caCertificate
                    )
                ) {
                    LOG.debug(
                        "OCSPResponse matched certificate with serialnumber {} and subject {}",
                        certificate.serialNumber,
                        certificate.subjectX500Principal
                    )
                    return certificate
                }
            }
        }
        LOG.debug(
            "We don't have a certificate with serial number {} (0x{}) in the document, ignoring the OCSP response for this certificate",
            { certificateID.serialNumber },
            { certificateID.serialNumber.toString(HEX_RADIX) }
        )
        return null
    }

    fun getAllEncapsulatedCertificatesByPrincipal(doc: Document): Map<Principal, X509Certificate> {
        val values = XPathUtil.getNodeValuesAtPath(doc, namespaceContext, XPATH_ENCAPSULATED_CERTIFICATE_TEXT)
        val certificates: MutableMap<Principal, X509Certificate> = mutableMapOf()
        for (value in values) {
            val certificate = X509Utils.loadCertificate(ByteUtil.decodeBase64(value))
            certificates[certificate.subjectX500Principal] = certificate
            LOG.debug(
                "certificate with serialnumber {} (0x{}) and subject {} included in document",
                { certificate.serialNumber },
                { certificate.serialNumber.toString(HEX_RADIX) },
                { certificate.subjectX500Principal }
            )
        }
        return certificates
    }

    private fun isCertificateIssuedByTrustedIssuer(certificate: X509Certificate): Boolean {
        if (trustedCertificateAuthorities.containsKey(certificate.issuerX500Principal)) {
            val caCertificate = trustedCertificateAuthorities[certificate.issuerX500Principal]
            return try {
                certificate.verify(caCertificate!!.publicKey, BouncyCastleProvider.PROVIDER_NAME)
                true
            } catch (e: GeneralSecurityException) {
                LOG.warn("Certificate issued by untrusted issuer: {}", X509Utils.toString(certificate), e)
                false
            }
        }
        return false
    }

    private fun validatePaths(doc: Document) {
        pathMustNotBeNull(doc, "//dsig:Signature", "No signature in message.")
        pathMustNotBeNull(doc, "//xades:QualifyingProperties", "No XAdES signatures found.")
        if (XPathUtil.getNodeAtPath(
                doc,
                namespaceContext,
                "//dsig:Signature/dsig:Object/xades:QualifyingProperties"
            ) == null &&
            XPathUtil.getNodeAtPath(
                    doc,
                    namespaceContext,
                    "//dsig:Signature[ancestor::xades:QualifyingProperties]"
                ) == null
        ) {
            throw RuntimeException("All signatures in the message must either be an XAdES signature itself or part of another XAdES signature.")
        }
        pathMustNotBeNull(
            doc,
            "$XPATH_PROPERTIES_UNSIGNED_SIGNATURE/*",
            "No XAdES unsigned signature properties to verify."
        )
        pathMustNotBeNull(doc, XPATH_SIGNATURE_TIMESTAMP, NOT_XADES_X_L)
        pathMustNotBeNull(doc, XPATH_SIGNATURE_COUNTERSIGNATURE, NOT_XADES_X_L)
        pathMustNotBeNull(doc, XPATH_ENCAPSULATED_CERTIFICATE, NOT_XADES_X_L)
        pathMustNotBeNull(doc, XPATH_ENCAPSULATED_OCSP, NOT_XADES_X_L)
    }

    private fun pathMustNotBeNull(doc: Document, path: String, message: String) {
        if (XPathUtil.countAtPath(doc, namespaceContext, path) == 0) {
            throw RuntimeException(message)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(XAdESVerifier::class.java)
        const val ETSI_NS = "http://uri.etsi.org/01903"
        const val XADES_NS = "$ETSI_NS/v1.3.2#"
        const val DSS_NS = "urn:oasis:names:tc:dss:1.0:core:schema"
        private const val NOT_XADES_X_L =
            "The XAdES QualifyingProperties of the signature does not conform to the XAdES-X-L form. Make sure the required elements are included and check for possible XAdES schema validation errors."
        private const val XPATH_PARENT_SIGNATURE_VALUE_ID =
            "//dsig:Signature/dsig:SignatureValue/attribute::*[translate(local-name(), 'ID','id') = 'id']"
        const val XPATH_PROPERTIES_UNSIGNED_SIGNATURE =
            "//xades:QualifyingProperties/xades:UnsignedProperties/xades:UnsignedSignatureProperties"
        const val XPATH_SIGNATURE_TIMESTAMP =
            "$XPATH_PROPERTIES_UNSIGNED_SIGNATURE/xades:SignatureTimeStamp/xades:XMLTimeStamp/dss:Timestamp/dsig:Signature"
        const val XPATH_SIGNATURE_COUNTERSIGNATURE =
            "$XPATH_PROPERTIES_UNSIGNED_SIGNATURE/xades:CounterSignature/dsig:Signature"
        const val XPATH_ENCAPSULATED_CERTIFICATE =
            "$XPATH_PROPERTIES_UNSIGNED_SIGNATURE/xades:CertificateValues/xades:EncapsulatedX509Certificate"
        const val XPATH_ENCAPSULATED_CERTIFICATE_TEXT = "$XPATH_ENCAPSULATED_CERTIFICATE/text()"
        const val XPATH_ENCAPSULATED_OCSP =
            "$XPATH_PROPERTIES_UNSIGNED_SIGNATURE/xades:RevocationValues/xades:OCSPValues/xades:EncapsulatedOCSPValue"
        private const val XPATH_ENCAPSULATED_OCSP_TEXT = "$XPATH_ENCAPSULATED_OCSP/text()"
        const val XPATH_TIMESTAMP_TEXT = "$XPATH_SIGNATURE_TIMESTAMP/dsig:Object/dss:TstInfo/dss:CreationTime/text()"
        const val XPATH_SERIALNUMBER_TEXT = "$XPATH_SIGNATURE_TIMESTAMP/dsig:Object/dss:TstInfo/dss:SerialNumber/text()"
        private const val THE_CERTIFICATE = "The certificate "
        const val HEX_RADIX = 16
        const val HELSE_ID_ISSUER = "https://helseid-sts.nhn.no"
        const val HELSE_ID_ORGNO = "994598759"
        val namespaceContext: NamespaceContext = DelegatingNamespaceContext(
            "dsig", Constants.SignatureSpecNS,
            "xades", XADES_NS,
            "dss", DSS_NS,
            "mh", "http://www.kith.no/xmlstds/msghead/2006-05-24",
            "bas", "http://www.kith.no/xmlstds/base64container"
        )
    }
}

enum class CertificateType {
    PERSONAL, ENTERPRISE, CA, UNKNOWN
}
