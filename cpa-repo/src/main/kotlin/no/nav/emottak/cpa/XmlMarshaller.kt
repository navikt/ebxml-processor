package no.nav.emottak.cpa

import java.io.StringWriter
import javax.xml.bind.JAXBContext
import javax.xml.stream.XMLInputFactory

val xmlMarshaller = XmlMarshaller()

fun marshal(objekt: Any) = xmlMarshaller.marshal(objekt)
fun <T> unmarshal(xml: String, clazz: Class<T>): T = xmlMarshaller.unmarshal(xml, clazz)

class XmlMarshaller {

    companion object {
        private val jaxbContext = JAXBContext.newInstance(
            org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.ObjectFactory::class.java,
            org.xmlsoap.schemas.soap.envelope.ObjectFactory::class.java,
            org.w3._1999.xlink.ObjectFactory::class.java,
            org.w3._2009.xmldsig11_.ObjectFactory::class.java
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

    fun <T> unmarshal(xml: String, clazz: Class<T>): T {
        val reader = XMLInputFactory.newInstance().createXMLStreamReader(xml.reader())
        return synchronized(unmarshlingMonitor) {
            unmarshaller.unmarshal(reader, clazz).value
        }
    }
}
