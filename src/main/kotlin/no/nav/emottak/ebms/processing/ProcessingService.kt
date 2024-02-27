package no.nav.emottak.ebms.processing

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.Acknowledgment
import no.nav.emottak.ebms.EbmsFail
import no.nav.emottak.ebms.EbmsMessage
import no.nav.emottak.ebms.PayloadMessage
import no.nav.emottak.ebms.PayloadProcessingClient
import no.nav.emottak.ebms.logger
import no.nav.emottak.ebms.model.EbmsAcknowledgment
import no.nav.emottak.ebms.model.EbmsBaseMessage
import no.nav.emottak.ebms.model.EbmsMessageError
import no.nav.emottak.ebms.model.EbmsPayloadMessage
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.melding.model.Direction
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.Feil
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.PartyId
import no.nav.emottak.melding.model.PayloadProcessing
import no.nav.emottak.melding.model.PayloadRequest
import no.nav.emottak.melding.model.PayloadResponse
import no.nav.emottak.melding.model.SendInResponse
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SeverityType
import java.util.UUID

class ProcessingService(val httpClient: PayloadProcessingClient) {

    private fun payloadMessage(
        payloadMessage: EbmsPayloadMessage,
        payloadProcessing: PayloadProcessing
    ): PayloadResponse {
        val payloads = payloadMessage.attachments
        val messageHeader = payloadMessage.messageHeader
        val payload = payloads.first()

        val payloadRequest = PayloadRequest(
            Direction.IN,
            messageId = messageHeader.messageData.messageId,
            conversationId = messageHeader.conversationId,
            processing = payloadProcessing,
            payloadId = payload.contentId,
            payload = payload.payload
        )
        // TODO do something with the response?
        return runBlocking {
            httpClient.postPayloadRequest(payloadRequest)
        }
    }

    private fun payloadMessage2(
        payloadMessage: PayloadMessage,
        payloadProcessing: PayloadProcessing,
        direction: Direction
    ): PayloadMessage {
        return runCatching {
            val payloadRequest = PayloadRequest(
                direction,
                messageId = payloadMessage.messageId,
                conversationId = payloadMessage.conversationId,
                processing = payloadProcessing,
                payloadId = UUID.randomUUID().toString(),
                payload = payloadMessage.payload.payload
            )
            // TODO do something with the response?
            runBlocking {
                httpClient.postPayloadRequest(payloadRequest)
            }
        }.onFailure {
            logger().error("Processing failed: ${it.message}", it)
            if (it !is EbmsException) throw EbmsException("Processing has failed", exception =  it)
            throw it
        }.map { payloadResponse ->
            if (payloadResponse.error != null) {
                throw EbmsException(listOf(payloadResponse.error!!))
            } else {
                payloadMessage.copy(payload = payloadMessage.payload.copy(payload = payloadResponse.processedPayload))
            }
        }.getOrThrow()
    }


    private fun acknowledgment(acknowledgment: Acknowledgment) {
    }

    private fun fail(fail: EbmsFail) {
    }

    fun processSync(message: EbmsBaseMessage, payloadProcessing: PayloadProcessing?): PayloadResponse {
        if (payloadProcessing == null) throw Exception("Processing information is missing for ${message.messageHeader.messageData.messageId}")
        return payloadMessage(message as EbmsPayloadMessage, payloadProcessing!!)
    }

    fun processSyncIn2(message: PayloadMessage, payloadProcessing: PayloadProcessing?): PayloadMessage {
        if (payloadProcessing == null) throw Exception("Processing information is missing for ${message.messageId}")
        return payloadMessage2(message, payloadProcessing, Direction.IN)
    }

    fun proccessSyncOut2(payloadMessage: PayloadMessage, payloadProcessing: PayloadProcessing?): PayloadMessage {
        if (payloadProcessing == null) throw Exception("Processing information is missing for ${payloadMessage.messageId}")
        return payloadMessage2(payloadMessage, payloadProcessing, Direction.OUT)
    }

    fun processAsync(message: EbmsMessage, payloadProcessing: PayloadProcessing?) {
        when (message) {
            is Acknowledgment -> acknowledgment(message)
            is EbmsFail -> fail(message)
            is PayloadMessage -> payloadMessage2(message, payloadProcessing!!,Direction.IN)
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
