package no.nav.emottak.message.xml

import com.sun.xml.bind.marshaller.NamespacePrefixMapper
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xmlsoap.schemas.soap.envelope.Envelope
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBElement
import javax.xml.bind.Marshaller
import javax.xml.stream.XMLInputFactory

val xmlMarshaller = XmlMarshaller()

fun marshal(objekt: Any) = xmlMarshaller.marshal(objekt)
fun <T> unmarshal(xml: String, clazz: Class<T>): T = xmlMarshaller.unmarshal(xml, clazz)

class XmlMarshaller {

    companion object {
        private val jaxbContext = JAXBContext.newInstance(
            org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.ObjectFactory::class.java,
            org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ObjectFactory::class.java,
            org.xmlsoap.schemas.soap.envelope.ObjectFactory::class.java,
            org.w3._1999.xlink.ObjectFactory::class.java,
            org.w3._2009.xmldsig11_.ObjectFactory::class.java
        )

        private val marshaller = jaxbContext.createMarshaller().apply {
            setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
            setProperty("com.sun.xml.bind.namespacePrefixMapper", EbXMLNamespacePrefixMapper())
        }
        private val unmarshaller = jaxbContext.createUnmarshaller()
        private val marshlingMonitor = Any()
        private val unmarshlingMonitor = Any()
    }

    fun marshal(objekt: Any): String {
        val writer = StringWriter()
        synchronized(marshlingMonitor) {
            marshaller.marshal(objekt, writer)
        }
        return writer.toString()
    }

    fun marshal(envelope: Envelope): Document {
        val out = ByteArrayOutputStream()
        synchronized(marshlingMonitor) {
            marshaller.marshal(envelope, out)
        }
        return getDocumentBuilder().parse(ByteArrayInputStream(out.toByteArray()))
    }

    fun <T> unmarshal(xml: String, clazz: Class<T>): T {
        val reader = XMLInputFactory.newInstance().createXMLStreamReader(xml.reader())
        return synchronized(unmarshlingMonitor) {
            unmarshaller.unmarshal(reader, clazz).value
        }
    }

    fun <T> unmarshal(document: Node): T {
        val unmarshalled = synchronized(unmarshlingMonitor) {
            unmarshaller.unmarshal(document)
        }
        return if (unmarshalled is JAXBElement<*>) (unmarshalled as JAXBElement<T>).value else unmarshalled as T
    }
}

class EbXMLNamespacePrefixMapper : NamespacePrefixMapper() {
    private val namespaceMap = mapOf(
        "http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd" to "eb",
        "http://www.oasis-open.org/committees/ebxml-cppa/schema/cpp-cpa-2_0.xsd" to "cppa",
        "http://www.w3.org/2000/09/xmldsig#" to "ds",
        "http://www.w3.org/1999/xlink" to "xlink",
        "http://schemas.xmlsoap.org/soap/envelope/" to "SOAP"
    )

    override fun getPreferredPrefix(namespaceUri: String?, suggestion: String?, requirePrefix: Boolean): String? {
        return namespaceMap[namespaceUri]
            ?: suggestion?.takeIf { it.isNotEmpty() }
            ?: namespaceUri?.let { "ns${it.hashCode().toUInt()}" }
            ?: "ns0"
    }
}
