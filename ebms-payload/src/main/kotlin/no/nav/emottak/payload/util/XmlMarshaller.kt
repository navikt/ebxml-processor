package no.nav.emottak.payload.util

import java.io.StringWriter
import javax.xml.bind.JAXBContext
import javax.xml.stream.XMLInputFactory

val xmlMarshaller = XmlMarshaller()

fun marshal(objekt: Any) = xmlMarshaller.marshal(objekt)
fun <T> unmarshal(xml: String, clazz: Class<T>): T = xmlMarshaller.unmarshal(xml, clazz)
fun <T> unmarshal(xml: ByteArray, clazz: Class<T>): T = unmarshal(String(xml), clazz)

class XmlMarshaller {

    companion object {
        private val jaxbContext = JAXBContext.newInstance(
            org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.ObjectFactory::class.java,
            org.xmlsoap.schemas.soap.envelope.ObjectFactory::class.java,
            org.w3._1999.xlink.ObjectFactory::class.java,
            org.w3._2009.xmldsig11_.ObjectFactory::class.java,
            no.trygdeetaten.xml.eiff._1.ObjectFactory::class.java,
            no.kith.xmlstds.msghead._2006_05_24.ObjectFactory::class.java,
            no.nav.tjeneste.ekstern.frikort.v1.types.ObjectFactory::class.java

        )
        private val marshaller = jaxbContext.createMarshaller()
        private val unmarshaller = jaxbContext.createUnmarshaller()
    }

    fun marshal(objekt: Any): String {
        val writer = StringWriter()
        marshaller.marshal(objekt, writer)
        return writer.toString()
    }

    fun <T> unmarshal(xml: String, clazz: Class<T>): T {
        val reader = XMLInputFactory.newInstance().createXMLStreamReader(xml.reader())
        return unmarshaller.unmarshal(reader, clazz).value
    }
}
