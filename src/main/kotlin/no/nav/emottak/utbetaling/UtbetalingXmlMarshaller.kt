package no.nav.emottak.utbetaling

import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.meldinger.v1.FinnBrukersUtbetalteYtelserRequest
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.meldinger.v1.FinnUtbetalingListeRequest
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.FinnUtbetalingListe
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import javax.xml.bind.JAXBContext
import javax.xml.stream.XMLInputFactory

val UtbetalingXmlMarshaller = XmlMarshaller()

fun marshal(objekt: Any) = UtbetalingXmlMarshaller.marshal(objekt)
fun <T> unmarshal(xml: String, clazz: Class<T>): T = UtbetalingXmlMarshaller.unmarshal(xml, clazz)

class XmlMarshaller {

    companion object {
        private val jaxbContext = JAXBContext.newInstance(
            FinnUtbetalingListeRequest::class.java,
            FinnBrukersUtbetalteYtelserRequest::class.java,
            FinnUtbetalingListe::class.java,
            org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.ObjectFactory::class.java,
            no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.meldinger.v1.ObjectFactory::class.java,
            no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.feil.v1.ObjectFactory::class.java,
            no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.ObjectFactory::class.java,
            org.xmlsoap.schemas.soap.envelope.ObjectFactory::class.java,
            no.kith.xmlstds.msghead._2006_05_24.ObjectFactory::class.java,
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

    fun marshalToByteArray(objekt: Any): ByteArray {
        return ByteArrayOutputStream().use {
            synchronized(marshlingMonitor) {
                marshaller.marshal(objekt, it)
            }
            it.toByteArray()
        }
    }

    fun <T> unmarshal(xml: String, clazz: Class<T>): T {
        val reader = XMLInputFactory.newInstance().createXMLStreamReader(xml.reader())
        return synchronized(unmarshlingMonitor) {
            unmarshaller.unmarshal(reader, clazz).value
        }
    }
}
