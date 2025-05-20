package no.nav.emottak.payload.helseid.util.util.xades

import no.nav.emottak.payload.helseid.util.lang.ByteUtil
import no.nav.emottak.payload.helseid.util.security.X509Utils
import no.nav.emottak.payload.helseid.util.util.XPathUtil
import no.nav.emottak.payload.helseid.util.util.xades.XAdESVerifier.Companion.HEX_RADIX
import no.nav.emottak.payload.helseid.util.util.xades.XAdESVerifier.Companion.XPATH_ENCAPSULATED_CERTIFICATE_TEXT
import no.nav.emottak.payload.helseid.util.util.xades.XAdESVerifier.Companion.namespaceContext
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import java.security.Principal
import java.security.cert.X509Certificate

val log = LoggerFactory.getLogger("no.nav.emottak.payload.helseid.util.util.xades.XAdESUtil")

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
