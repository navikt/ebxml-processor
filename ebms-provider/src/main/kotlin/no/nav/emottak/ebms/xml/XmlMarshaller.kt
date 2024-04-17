package no.nav.emottak.ebms.xml

import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xmlsoap.schemas.soap.envelope.Envelope
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBElement
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
            org.w3._2009.xmldsig11_.ObjectFactory::class.java,
            no.kith.xmlstds.msghead._2006_05_24.ObjectFactory::class.java,
            no.nav.tjeneste.ekstern.frikort.v1.types.ObjectFactory::class.java
        )

        private val marshaller = jaxbContext.createMarshaller()
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
