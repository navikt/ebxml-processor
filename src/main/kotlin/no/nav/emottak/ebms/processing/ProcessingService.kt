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
import no.nav.emottak.melding.model.PartyId
import no.nav.emottak.melding.model.PayloadRequest
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader

class ProcessingService(val httpClient: PayloadProcessingClient) {


    private fun payloadMessage(payloadMessage: EbMSPayloadMessage) {
        val payloads = payloadMessage.attachments
        val header = payloadMessage.messageHeader.payloadRequestHeader(payloadMessage.contentID)
        payloads.forEach { payload ->
            val payloadRequest = PayloadRequest(
                header = header,
                payloadId = payload.contentId,
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


fun MessageHeader.payloadRequestHeader(contentId:String): Header {
    return Header(
        messageId = this.messageData.messageId ?: throw BadRequestException("MessageID mangler fra header"),
        cpaId = this.cpaId ?: throw BadRequestException("CPAID mangler fra header"),
        conversationId = this.conversationId,
        to = Party(
            role = this.to.role ?: throw BadRequestException("Melding mangler role for en eller flere parter"),
            partyId = listOf(PartyId(
            type = this.to.partyId.firstOrNull()?.type ?: throw BadRequestException("Melding mangler to partyId"),
            value = this.to.partyId.firstOrNull()?.value ?: throw BadRequestException("Melding mangler to partyId"))
            )),
        from = Party(
            role = this.from.role ?: throw BadRequestException("Melding mangler role for en eller flere parter"),
            partyId = listOf(PartyId(
                type = this.from.partyId.firstOrNull()?.type ?: throw BadRequestException("Melding mangler from partyId"),
                value = this.from.partyId.firstOrNull()?.value ?: throw BadRequestException("Melding mangler from partyId"),
            ))
        ),
        service = this.service.value ?: throw BadRequestException("Service mangler fra header"),
        action = this.action
    )
}
