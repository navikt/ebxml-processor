package no.nav.emottak.ebms.processing

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.runBlocking
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
        payloadProcessing: PayloadProcessing,direction: Direction
    ): EbmsMessage {
        return runCatching {
             val payloadRequest = PayloadRequest(
            Direction.OUT,
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
            if (it is EbmsException) {
                logger().error("Processing failed: ${it.message}", it)
                return payloadMessage
                    .createFail(listOf(Feil(it.errorCode, it.errorCode.description)))
            } else {
                logger().error("Processing failed: ${it.message}", it)
                return payloadMessage.createFail(listOf(Feil(ErrorCode.UNKNOWN, "Processing failed: ${it.message}")))
            }
        }.map { payloadResponse ->
            if (payloadResponse.error != null) payloadMessage.createFail(listOf(payloadResponse.error!!))
            else
                payloadMessage.copy(payload = payloadMessage.payload.copy(payload = payloadResponse.processedPayload))
        }.getOrThrow()
    }

    private fun acknowledgment(acknowledgment: EbmsAcknowledgment) {
    }

    private fun fail(fail: EbmsMessageError) {
    }

    fun processSync(message: EbmsBaseMessage, payloadProcessing: PayloadProcessing?): PayloadResponse {
        if (payloadProcessing == null) throw Exception("Processing information is missing for ${message.messageHeader.messageData.messageId}")
        return payloadMessage(message as EbmsPayloadMessage, payloadProcessing!!)
    }

    fun processSyncIn2(message: PayloadMessage, payloadProcessing: PayloadProcessing?): EbmsMessage {
        if (payloadProcessing == null) throw Exception("Processing information is missing for ${message.messageId}")
        return payloadMessage2(message, payloadProcessing,Direction.IN)

    }

    fun proccessSyncOut(sendInResponse: SendInResponse, processing: PayloadProcessing): PayloadResponse {
        val payloadRequest = PayloadRequest(
            Direction.OUT,
            messageId = sendInResponse.messageId,
            conversationId = sendInResponse.conversationId,
            processing = processing,
            payloadId = UUID.randomUUID().toString(),
            payload = sendInResponse.payload
        )
        // TODO do something with the response?
        return runBlocking {
            httpClient.postPayloadRequest(payloadRequest)
        }
    }

    fun proccessSyncOut(payloadMessage: PayloadMessage,payloadProcessing: PayloadProcessing?): EbmsMessage {
        if (payloadProcessing == null) throw Exception("Processing information is missing for ${payloadMessage.messageId}")
        return payloadMessage2(payloadMessage,payloadProcessing,Direction.OUT)
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
