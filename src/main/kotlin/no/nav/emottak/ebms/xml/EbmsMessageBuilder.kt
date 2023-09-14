package no.nav.emottak.ebms.xml

import no.nav.emottak.ebms.model.EbMSDocument
import org.xmlsoap.schemas.soap.envelope.Envelope

class EbmsMessageBuilder {
    val xmlMarshaller = XmlMarshaller()


    fun buildEbmMessage(dokument: EbMSDocument): Envelope {
        println(String( dokument.dokument))
        return xmlMarshaller.unmarshal( String( dokument.dokument), Envelope::class.java)
    }
}