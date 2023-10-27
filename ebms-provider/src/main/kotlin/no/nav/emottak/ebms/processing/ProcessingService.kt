package no.nav.emottak.ebms.processing

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.PayloadProcessingClient
import no.nav.emottak.ebms.model.EbMSBaseMessage
import no.nav.emottak.ebms.model.EbMSMessageError
import no.nav.emottak.ebms.model.EbMSPayloadMessage
import no.nav.emottak.ebms.model.EbmsAcknowledgment
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.PayloadRequest
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader

class ProcessingService(val httpClient: PayloadProcessingClient) {


    private fun payloadMessage(payloadMessage: EbMSPayloadMessage) {
        val payloads = payloadMessage.attachments
        val header = payloadMessage.messageHeader.payloadRequestHeader()
        payloads.forEach { payload ->
            val payloadRequest = PayloadRequest(
                header = header,
                payload = payload.dataSource
            )
            //TODO do something with the response?
            runBlocking {
                httpClient.postPayloadRequest(payloadRequest)
            }
        }
    }

    private fun acknowledgment(acknowledgment: EbmsAcknowledgment) {

    }

    private fun fail(fail:EbMSMessageError) {

    }

    fun process(message: EbMSBaseMessage) {
        when (message) {
            is EbmsAcknowledgment ->  acknowledgment(message)
            is EbMSMessageError -> fail(message)
            is EbMSPayloadMessage -> payloadMessage(message)
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
            partyId = this.to.partyId.firstOrNull()?.value ?: throw BadRequestException("Melding mangler to partyId"),
            role = this.to.role ?: throw BadRequestException("Melding mangler role for en eller flere parter")
        ),
        from = Party(
            partyType = this.from.partyId.firstOrNull()?.type ?: throw BadRequestException("Melding mangler from partyId"),
            partyId = this.from.partyId.firstOrNull()?.value ?: throw BadRequestException("Melding mangler from partyId"),
            role = this.from.role ?: throw BadRequestException("Melding mangler role for en eller flere parter")
        ),
        service = this.service.value ?: throw BadRequestException("Service mangler fra header"),
        action = this.action
    )
}
