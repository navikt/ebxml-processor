package no.nav.emottak.ebms.xml

import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.EbMSPayloadMessage
import no.nav.emottak.ebms.model.ackRequested
import no.nav.emottak.ebms.model.header
import org.xmlsoap.schemas.soap.envelope.Envelope
import java.time.LocalDateTime

class EbmsMessageBuilder {
    val xmlMarshaller = XmlMarshaller()

    fun buildEbmMessage(dokument: EbMSDocument): EbMSPayloadMessage? {
        println(dokument.dokument.toString())

        val envelope: Envelope = xmlMarshaller.unmarshal(dokument.dokument)
        return EbMSPayloadMessage(dokument.dokument,envelope.header(),envelope.ackRequested(),dokument.attachments, LocalDateTime.now() )
    }
}