package no.nav.emottak.ebms.processing

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.PayloadProcessingClient
import no.nav.emottak.ebms.logger
import no.nav.emottak.ebms.model.Acknowledgment
import no.nav.emottak.ebms.model.EbmsFail
import no.nav.emottak.ebms.model.EbmsMessage
import no.nav.emottak.ebms.model.PayloadMessage
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.melding.model.Direction
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.PartyId
import no.nav.emottak.melding.model.PayloadProcessing
import no.nav.emottak.melding.model.PayloadRequest
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import java.util.UUID

class ProcessingService(val httpClient: PayloadProcessingClient) {

    private fun processMessage(
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
            throw EbmsException("Processing has failed", exception = it)
        }.map { payloadResponse ->
            if (payloadResponse.error != null) {
                throw EbmsException(listOf(payloadResponse.error!!))
            } else {
                payloadMessage.copy(payload = payloadMessage.payload.copy(payload = payloadResponse.processedPayload))
            }
        }.getOrElse {
            throw EbmsException("Processing has failed", exception = it)
        }
    }

    private fun acknowledgment(acknowledgment: Acknowledgment) {
    }

    private fun fail(fail: EbmsFail) {
    }

    fun processSyncIn(message: PayloadMessage, payloadProcessing: PayloadProcessing?): PayloadMessage {
        if (payloadProcessing == null) throw Exception("Processing information is missing for ${message.messageId}")
        return processMessage(message, payloadProcessing, Direction.IN)
    }

    fun proccessSyncOut(payloadMessage: PayloadMessage, payloadProcessing: PayloadProcessing?): PayloadMessage {
        if (payloadProcessing == null) throw Exception("Processing information is missing for ${payloadMessage.messageId}")
        return processMessage(payloadMessage, payloadProcessing, Direction.OUT)
    }

    fun processAsync(message: EbmsMessage, payloadProcessing: PayloadProcessing?) {
        if (payloadProcessing == null) throw Exception("Processing information is missing for ${message.messageId}")
        when (message) {
            is Acknowledgment -> acknowledgment(message)
            is EbmsFail -> fail(message)
            is PayloadMessage -> processMessage(message, payloadProcessing!!, Direction.IN)
        }
    }
}


