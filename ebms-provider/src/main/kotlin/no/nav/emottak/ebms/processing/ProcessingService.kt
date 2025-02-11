package no.nav.emottak.ebms.processing

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.ebms.PayloadProcessingClient
import no.nav.emottak.ebms.log
import no.nav.emottak.ebms.logger
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.message.model.Acknowledgment
import no.nav.emottak.message.model.Addressing
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.EbmsFail
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.PayloadProcessing
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.message.model.PayloadResponse

class ProcessingService(private val httpClient: PayloadProcessingClient) {

    private suspend fun processMessage(
        payloadMessage: PayloadMessage,
        payloadProcessing: PayloadProcessing,
        direction: Direction,
        addressing: Addressing
    ): Pair<PayloadMessage, Direction> {
        return try {
            val payloadRequest = PayloadRequest(
                direction,
                messageId = payloadMessage.messageId,
                conversationId = payloadMessage.conversationId,
                processing = payloadProcessing,
                addressing = addressing,
                payload = payloadMessage.payload
            )
            val payloadResponse = httpClient.postPayloadRequest(payloadRequest)

            if (payloadResponse.error != null) {
                log.error(
                    payloadMessage.marker(),
                    "Payload processing failed (setting errorAction to ${payloadProcessing.processConfig.errorAction}): ${payloadResponse.error!!.descriptionText}"
                )
                return Pair(
                    payloadMessage.convertToErrorActionMessage(
                        payloadResponse.processedPayload!!,
                        payloadProcessing.processConfig.errorAction!!
                    ),
                    Direction.OUT
                )
            }

            Pair(
                payloadMessage.copy(
                    payload = withContext(Dispatchers.IO) {
                        payloadResponse.processedPayload!!
                    }
                ),
                direction
            )
        } catch (clientRequestException: ClientRequestException) {
            logger().error(
                payloadMessage.marker(),
                "Processing failed: ${clientRequestException.message}",
                clientRequestException
            )
            when (clientRequestException.response.status) {
                HttpStatusCode.BadRequest -> {
                    return Pair(
                        payloadMessage.convertToErrorActionMessage(
                            clientRequestException.retrieveReturnableApprecResponse(direction).processedPayload!!,
                            payloadProcessing.processConfig!!.errorAction!!
                        ),
                        Direction.OUT
                    )
                }

                else -> throw EbmsException("Processing has failed", exception = clientRequestException)
            }
        } catch (ebmsException: EbmsException) {
            throw ebmsException
        } catch (exception: Exception) {
            throw EbmsException("Processing has failed", exception = exception)
        }
    }

    private suspend fun ClientRequestException.retrieveReturnableApprecResponse(
        direction: Direction
    ): PayloadResponse = withContext(Dispatchers.IO) {
        this@retrieveReturnableApprecResponse.response.body<PayloadResponse?>().takeIf { payloadResponse ->
            payloadResponse != null &&
                payloadResponse.apprec &&
                payloadResponse.processedPayload != null &&
                direction == Direction.IN
        } ?: throw EbmsException("Processing has failed", exception = this@retrieveReturnableApprecResponse)
    }

    private fun acknowledgment(acknowledgment: Acknowledgment) {
    }

    private fun fail(fail: EbmsFail) {
    }

    suspend fun processSyncIn(
        payloadMessage: PayloadMessage,
        payloadProcessing: PayloadProcessing?
    ): Pair<PayloadMessage, Direction> {
        if (payloadProcessing == null) throw Exception("Processing information is missing for ${payloadMessage.messageId}")
        return when (payloadProcessing.hasActionableProcessingSteps()) {
            true -> processMessage(payloadMessage, payloadProcessing, Direction.IN, payloadMessage.addressing)
            false -> payloadMessage to Direction.IN
        }
    }

    suspend fun proccessSyncOut(payloadMessage: PayloadMessage, payloadProcessing: PayloadProcessing?): PayloadMessage {
        if (payloadProcessing == null) throw Exception("Processing information is missing for ${payloadMessage.messageId}")
        return when (payloadProcessing.hasActionableProcessingSteps()) {
            true -> processMessage(payloadMessage, payloadProcessing, Direction.OUT, payloadMessage.addressing).first
            false -> payloadMessage
        }
    }

    suspend fun processAsync(message: EbmsMessage, payloadProcessing: PayloadProcessing?) {
        if (payloadProcessing == null) throw Exception("Processing information is missing for ${message.messageId}")
        when (message) {
            is Acknowledgment -> acknowledgment(message)
            is EbmsFail -> fail(message)
            is PayloadMessage -> processMessage(message, payloadProcessing, Direction.IN, message.addressing)
        }
    }
}

private fun PayloadProcessing.hasActionableProcessingSteps(): Boolean =
    this.processConfig != null &&
        (this.processConfig!!.signering || this.processConfig!!.kryptering || this.processConfig!!.komprimering)

private fun PayloadMessage.convertToErrorActionMessage(payload: Payload, errorAction: String): PayloadMessage =
    this.copy(
        payload = payload,
        addressing = this.addressing.copy(
            action = errorAction,
            to = this.addressing.from,
            from = this.addressing.to
        )
    )
