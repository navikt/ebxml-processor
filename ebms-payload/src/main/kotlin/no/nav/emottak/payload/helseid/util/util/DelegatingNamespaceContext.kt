package no.nav.emottak.payload.helseid.util.util

import org.apache.xml.security.utils.Constants
import java.util.Objects
import javax.xml.namespace.NamespaceContext

val namespaceContext: NamespaceContext = DelegatingNamespaceContext(
    "dsig", Constants.SignatureSpecNS,
    "xades", "http://uri.etsi.org/01903/v1.3.2#",
    "dss", "urn:oasis:names:tc:dss:1.0:core:schema",
    "mh", "http://www.kith.no/xmlstds/msghead/2006-05-24",
    "bas", "http://www.kith.no/xmlstds/base64container"
)

class DelegatingNamespaceContext(vararg prefixesAndNamespaces: String) : NamespaceContext {
    private val bindings = LinkedHashMap<String, String>()
    private var hashcode: Int

    init {
        var i = 0
        while (i < prefixesAndNamespaces.size) {
            bindings[prefixesAndNamespaces[i]] = prefixesAndNamespaces[i + 1]
            i += 2
        }
        hashcode = calculateHashCode()
    }

    override fun getNamespaceURI(prefix: String): String {
        requireNotNull(prefix) { "Prefix must not be null" }
        return bindings[prefix] ?: ""
    }

    override fun getPrefix(namespaceURI: String): String {
        requireNotNull(namespaceURI) { "Namespace URI must not be null" }
        return bindings.entries.firstOrNull { it.value == namespaceURI }?.key ?: ""
    }

    override fun getPrefixes(namespaceURI: String): Iterator<String> {
        requireNotNull(namespaceURI) { "Namespace URI must not be null" }
        return bindings.filterValues { it == namespaceURI }.keys.iterator()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as DelegatingNamespaceContext
        return hashcode == that.hashcode && bindings == that.bindings
    }

    override fun hashCode(): Int {
        return hashcode
    }

    private fun calculateHashCode(): Int {
        return Objects.hash(bindings.entries.map { it.key to it.value })
    }
}
