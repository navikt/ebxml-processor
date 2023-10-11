package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.processing.CPAValidationProcessor
import no.nav.emottak.ebms.processing.SertifikatsjekkProcessor
import no.nav.emottak.ebms.processing.SignatursjekkProcessor
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Acknowledgment
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.w3c.dom.Document

class EbmsAcknowledgment( override val messageHeader: MessageHeader,
                         val acknowledgment: Acknowledgment,private val dokument: Document? = null) : EbMSBaseMessage {



    fun process() {
        try {
            listOf(
                CPAValidationProcessor(this),
                SertifikatsjekkProcessor(this),
                SignatursjekkProcessor(dokument!!, this)
            )
                .forEach { it.processWithEvents() }

        }catch (ex: Exception) {
            return
        }
    }
    fun toEbmsDokument(): EbMSDocument? {
        return null
    }

}