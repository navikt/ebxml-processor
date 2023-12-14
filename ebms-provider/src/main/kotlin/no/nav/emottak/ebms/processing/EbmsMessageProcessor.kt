package no.nav.emottak.ebms.processing

import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.EbmsBaseMessage

class EbmsMessageProcessor(ebMSDocument: EbMSDocument, ebMSMessage: EbmsBaseMessage) {
    // TODO tenk over processor-sett, flow struktur, overall state oversikt

    val processCollection =
        listOf(
            CPAValidationProcessor(ebMSMessage),
            //     PayloadProcessor(ebMSMessage),
            SertifikatsjekkProcessor(ebMSMessage)
        )

    fun runAll() {
        processCollection.forEach { p -> p.processWithEvents() }
    }
}
