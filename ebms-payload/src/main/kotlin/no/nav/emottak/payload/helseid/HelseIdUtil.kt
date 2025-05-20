package no.nav.emottak.payload.helseid

import no.nav.emottak.payload.helseid.util.lang.ByteUtil
import no.nav.emottak.payload.helseid.util.security.X509Utils
import no.nav.emottak.payload.helseid.util.util.DelegatingNamespaceContext
import no.nav.emottak.payload.helseid.util.util.XPathUtil
import org.apache.xml.security.utils.Constants
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import java.io.File
import java.security.Principal
import java.security.cert.X509Certificate
import java.time.ZonedDateTime
import java.util.Date
import javax.xml.namespace.NamespaceContext

private val log = LoggerFactory.getLogger("no.nav.emottak.payload.helseid.HelseIdUtil")

private const val HEX_RADIX = 16
private const val XPATH_PROPERTIES_UNSIGNED_SIGNATURE =
    "//xades:QualifyingProperties/xades:UnsignedProperties/xades:UnsignedSignatureProperties"
private const val XPATH_ENCAPSULATED_CERTIFICATE =
    "$XPATH_PROPERTIES_UNSIGNED_SIGNATURE/xades:CertificateValues/xades:EncapsulatedX509Certificate"
private const val XPATH_ENCAPSULATED_CERTIFICATE_TEXT = "$XPATH_ENCAPSULATED_CERTIFICATE/text()"

/**
 * used to filter out the files and directories created by k8s secrets manager, like
 * @param f the file
 * @return true if the file name contains two dots
 */
fun isDotDot(f: File) = f.path.contains("..")

fun toDate(zonedDateTime: ZonedDateTime?): Date =
    if (zonedDateTime == null) Date() else Date.from(zonedDateTime.toInstant())

val namespaceContext: NamespaceContext = DelegatingNamespaceContext(
    "dsig", Constants.SignatureSpecNS,
    "xades", "http://uri.etsi.org/01903/v1.3.2#",
    "dss", "urn:oasis:names:tc:dss:1.0:core:schema",
    "mh", "http://www.kith.no/xmlstds/msghead/2006-05-24",
    "bas", "http://www.kith.no/xmlstds/base64container"
)

fun getAllEncapsulatedCertificatesByPrincipal(doc: Document): Map<Principal, X509Certificate> {
    val values = XPathUtil.getNodeValuesAtPath(doc, namespaceContext, XPATH_ENCAPSULATED_CERTIFICATE_TEXT)
    val certificates: MutableMap<Principal, X509Certificate> = mutableMapOf()
    for (value in values) {
        val certificate = X509Utils.loadCertificate(ByteUtil.decodeBase64(value))
        certificates[certificate.subjectX500Principal] = certificate
        log.debug(
            "certificate with serialnumber {} (0x{}) and subject {} included in document",
            { certificate.serialNumber },
            { certificate.serialNumber.toString(HEX_RADIX) },
            { certificate.subjectX500Principal }
        )
    }
    return certificates
}
