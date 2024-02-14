package no.nav.emottak.ebms.processing

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.PayloadProcessingClient
import no.nav.emottak.ebms.model.EbmsAcknowledgment
import no.nav.emottak.ebms.model.EbmsBaseMessage
import no.nav.emottak.ebms.model.EbmsMessageError
import no.nav.emottak.ebms.model.EbmsPayloadMessage
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.PartyId
import no.nav.emottak.melding.model.PayloadProcessing
import no.nav.emottak.melding.model.PayloadRequest
import no.nav.emottak.melding.model.PayloadResponse
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader

class ProcessingService(val httpClient: PayloadProcessingClient) {

    private fun payloadMessage(
        payloadMessage: EbmsPayloadMessage,
        payloadProcessing: PayloadProcessing
    ): PayloadResponse {
        val payloads = payloadMessage.attachments
        val messageHeader = payloadMessage.messageHeader
        val payload = payloads.first()

        val payloadRequest = PayloadRequest(
            messageId = messageHeader.messageData.messageId,
            conversationId = messageHeader.conversationId,
            processing = payloadProcessing,
            payloadId = payload.contentId,
            payload = payload.dataSource
        )
        // TODO do something with the response?
        return runBlocking {
            httpClient.postPayloadRequest(payloadRequest)
        }
    }

    private fun acknowledgment(acknowledgment: EbmsAcknowledgment) {
    }

    private fun fail(fail: EbmsMessageError) {
    }

    fun processSync(message: EbmsBaseMessage, payloadProcessing: PayloadProcessing?): PayloadResponse {
        if (payloadProcessing == null) throw Exception("Processing information is missing for ${message.messageHeader.messageData.messageId}")
        return payloadMessage(message as EbmsPayloadMessage, payloadProcessing!!)
    }

    fun processAsync(message: EbmsBaseMessage, payloadProcessing: PayloadProcessing?) {
        when (message) {
            is EbmsAcknowledgment -> acknowledgment(message)
            is EbmsMessageError -> fail(message)
            is EbmsPayloadMessage -> payloadMessage(message, payloadProcessing!!)
        }
    }
}

fun MessageHeader.payloadRequestHeader(): Header {
    return Header(
        messageId = this.messageData.messageId ?: throw BadRequestException("MessageID mangler fra header"),
        cpaId = this.cpaId ?: throw BadRequestException("CPAID mangler fra header"),
        conversationId = this.conversationId,
        to = Party(
            role = this.to.role ?: throw BadRequestException("Melding mangler role for en eller flere parter"),
            partyId = listOf(
                PartyId(
                    type = this.to.partyId.firstOrNull()?.type
                        ?: throw BadRequestException("Melding mangler to partyId"),
                    value = this.to.partyId.firstOrNull()?.value
                        ?: throw BadRequestException("Melding mangler to partyId")
                )
            )
        ),
        from = Party(
            role = this.from.role ?: throw BadRequestException("Melding mangler role for en eller flere parter"),
            partyId = listOf(
                PartyId(
                    type = this.from.partyId.firstOrNull()?.type
                        ?: throw BadRequestException("Melding mangler from partyId"),
                    value = this.from.partyId.firstOrNull()?.value
                        ?: throw BadRequestException("Melding mangler from partyId")
                )
            )
        ),
        service = this.service.value ?: throw BadRequestException("Service mangler fra header"),
        action = this.action
    )
}
