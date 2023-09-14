package no.nav.emottak.ebms.processing

import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.xml.EbmsMessageBuilder

class EbmsMessageProcessor {


    fun process(dokument: EbMSDocument) {
        val message = EbmsMessageBuilder().buildEbmMessage(dokument)
        println(message)
    }
}