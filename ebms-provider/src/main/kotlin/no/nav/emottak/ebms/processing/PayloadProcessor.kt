package no.nav.emottak.ebms.processing

import io.ktor.server.plugins.BadRequestException
import no.nav.emottak.ebms.model.EbMSPayloadMessage
import no.nav.emottak.ebms.postPayloadRequest
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.PayloadRequest
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.PartyId

class PayloadProcessor(override val ebMSMessage: EbMSPayloadMessage) : Processor(ebMSMessage) {
    override fun process() {
        val payloads = ebMSMessage.attachments
        val header = ebMSMessage.messageHeader.payloadRequestHeader()
        payloads.forEach { payload ->
            val payloadRequest = PayloadRequest(
                header = header,
                payload = payload.dataSource
            )
            //TODO do something with the response?
            val response = postPayloadRequest(payloadRequest)
        }
    }
}

fun MessageHeader.payloadRequestHeader(): Header {
    return Header(
        messageId = this.messageData.messageId ?: throw BadRequestException("MessageID mangler fra header"),
        cpaId = this.cpaId ?: throw BadRequestException("CPAID mangler fra header"),
        conversationId = this.conversationId,
        to = Party(
            partyType = this.to.partyId.firstOrNull()?.type ?: throw BadRequestException("Melding mangler to partyId"),
            partyId = this.to.partyId.firstOrNull()?.type ?: throw BadRequestException("Melding mangler to partyId"),
            role = this.to.role ?: throw BadRequestException("Melding mangler role for en eller flere parter")
        ),
        from = Party(
            partyType = this.from.partyId.firstOrNull()?.type ?: throw BadRequestException("Melding mangler from partyId"),
            partyId = this.from.partyId.firstOrNull()?.type ?: throw BadRequestException("Melding mangler from partyId"),
            role = this.from.role ?: throw BadRequestException("Melding mangler role for en eller flere parter")
        ),
        service = this.service.value ?: throw BadRequestException("Service mangler fra header"),
        action = this.action
    )
}
