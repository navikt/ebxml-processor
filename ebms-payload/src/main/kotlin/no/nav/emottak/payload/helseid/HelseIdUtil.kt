package no.nav.emottak.payload.helseid

import java.security.Principal
import java.security.cert.X509Certificate
import java.time.ZonedDateTime
import java.util.Date
import no.nav.emottak.payload.helseid.util.lang.ByteUtil
import no.nav.emottak.payload.helseid.util.security.X509Utils
import no.nav.emottak.payload.helseid.util.util.XPathUtil
import no.nav.emottak.payload.helseid.util.util.namespaceContext
import org.slf4j.LoggerFactory
import org.w3c.dom.Document

private val log = LoggerFactory.getLogger("no.nav.emottak.payload.helseid.HelseIdUtil")

private const val HEX_RADIX = 16
private const val XPATH_PROPERTIES_UNSIGNED_SIGNATURE =
    "//xades:QualifyingProperties/xades:UnsignedProperties/xades:UnsignedSignatureProperties"
private const val XPATH_ENCAPSULATED_CERTIFICATE =
    "$XPATH_PROPERTIES_UNSIGNED_SIGNATURE/xades:CertificateValues/xades:EncapsulatedX509Certificate"
private const val XPATH_ENCAPSULATED_CERTIFICATE_TEXT = "$XPATH_ENCAPSULATED_CERTIFICATE/text()"


fun toDate(zonedDateTime: ZonedDateTime?): Date =
    if (zonedDateTime == null) Date() else Date.from(zonedDateTime.toInstant())


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
