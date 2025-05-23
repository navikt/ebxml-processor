package no.nav.emottak.payload.helseid.util

import org.apache.xml.security.utils.Constants
import javax.xml.XMLConstants
import javax.xml.namespace.NamespaceContext

val msgHeadNamespaceContext: NamespaceContext = object : NamespaceContext {

    private val prefixToUri = mapOf(
        "dsig" to Constants.SignatureSpecNS,
        "xades" to "http://uri.etsi.org/01903/v1.3.2#",
        "dss" to "urn:oasis:names:tc:dss:1.0:core:schema",
        "mh" to "http://www.kith.no/xmlstds/msghead/2006-05-24",
        "bas" to "http://www.kith.no/xmlstds/base64container"
    )

    override fun getNamespaceURI(prefix: String?): String =
        prefixToUri[prefix] ?: XMLConstants.NULL_NS_URI

    override fun getPrefix(namespaceUri: String?): String? =
        prefixToUri.entries.firstOrNull { it.value == namespaceUri }?.key

    override fun getPrefixes(namespaceUri: String?): Iterator<String> =
        prefixToUri.filterValues { it == namespaceUri }.keys.iterator()
}
