package no.nav.emottak.ebms.processing

import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.EbMSMessage
import no.nav.emottak.ebms.xml.EbmsMessageBuilder

class EbmsMessageProcessor(ebMSMessage: EbMSMessage) {
    // TODO tenk over processor-sett, flow struktur, overall state oversikt

    val processCollection =
        listOf(
            AckRequestedProcessor(ebMSMessage),
            CPAValidationProcessor(ebMSMessage),
            PayloadProcessor(ebMSMessage),
            SertifikatsjekkProcessor(ebMSMessage),
            SignatursjekkProcessor(ebMSMessage),
        )

    fun runAll() {
        processCollection.forEach { p -> p.processWithEvents() }
    }

}