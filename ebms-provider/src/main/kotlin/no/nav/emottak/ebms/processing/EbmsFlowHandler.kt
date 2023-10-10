package no.nav.emottak.ebms.processing

import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.EbMSError
import no.nav.emottak.ebms.model.EbMSMessage
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Error
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ErrorList
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SeverityType

class EbmsFlowHandler(ebMSDocument: EbMSDocument, ebMSMessage: EbMSMessage) {
    // TODO tenk over processor-sett, flow struktur, overall state oversikt

    private val processCollection =
        listOf(
            CPAValidationProcessor(ebMSMessage),
            SertifikatsjekkProcessor(ebMSMessage),
            SignatursjekkProcessor(ebMSDocument, ebMSMessage),
            PayloadProcessor(ebMSMessage),
            AckRequestedProcessor(ebMSMessage),
        )

    fun run() {
        try {
            processCollection.forEach { p -> p.processWithEvents() }
        } catch (e: Processor.EbxmlProcessException) {

        }
    }



}