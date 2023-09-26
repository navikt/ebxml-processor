package no.nav.emottak.ebms.processing

import io.ktor.server.plugins.BadRequestException
import no.nav.emottak.ebms.model.EbMSMessage
import no.nav.emottak.ebms.postPayloadRequest
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.PayloadRequest
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.PartyId

class PayloadProcessor: Processor {
    override fun process(ebMSMessage: EbMSMessage) {
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
        messageId = this.id ?: throw BadRequestException("MessageID mangler fra header"),
        cpaId = this.cpaId ?: throw BadRequestException("CPAID mangler fra header"),
        conversationId = this.conversationId,
        to = Party(
            herID = this.to.partyId.herID(),
            role = this.to.role ?: throw BadRequestException("Melding mangler role for en eller flere parter")
        ),
        from = Party(
            herID = this.from.partyId.herID(),
            role = this.from.role ?: throw BadRequestException("Melding mangler role for en eller flere parter")
        ),
        service = this.service.value ?: throw BadRequestException("Service mangler fra header"),
        action = this.action
    )
}

fun List<PartyId>.herID(): String {
    return this.firstOrNull() { partyId -> partyId.type == "HER" }?.value ?: throw BadRequestException("Melding mangler HER-ID for en eller flere parter")
}
