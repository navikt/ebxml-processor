package no.nav.emottak.ebms.processing

import no.nav.emottak.ebms.model.EbMSBaseMessage
import no.nav.emottak.ebms.model.EbMSDocument

class EbmsMessageProcessor(ebMSDocument: EbMSDocument, ebMSMessage: EbMSBaseMessage) {
    // TODO tenk over processor-sett, flow struktur, overall state oversikt

    val processCollection =
        listOf(
            CPAValidationProcessor(ebMSMessage),
       //     PayloadProcessor(ebMSMessage),
            SertifikatsjekkProcessor(ebMSMessage),
            SignatursjekkProcessor(ebMSDocument.dokument, ebMSMessage),
        )

    fun runAll() {
        processCollection.forEach { p -> p.processWithEvents() }
    }

}