package no.nav.emottak.ebms.xml

import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.EbMSMessage
import no.nav.emottak.ebms.model.ackRequested
import no.nav.emottak.ebms.model.header
import org.xmlsoap.schemas.soap.envelope.Envelope

class EbmsMessageBuilder {
    val xmlMarshaller = XmlMarshaller()

    fun buildEbmMessage(dokument: EbMSDocument): EbMSMessage? {
        println(String( dokument.dokument))

        val envelope = xmlMarshaller.unmarshal( String( dokument.dokument), Envelope::class.java)
        return EbMSMessage(envelope.header(),envelope.ackRequested(),dokument.attachments )
    }
}