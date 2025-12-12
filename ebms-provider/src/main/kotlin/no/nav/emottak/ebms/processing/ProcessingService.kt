package no.nav.emottak.ebms.processing

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.ebms.PayloadProcessingClient
import no.nav.emottak.ebms.logger
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.PayloadProcessing
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.message.model.PayloadResponse
import no.nav.emottak.util.marker
import no.nav.emottak.utils.common.model.Addressing

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
                payload = payloadMessage.payload,
                requestId = payloadMessage.requestId
            )
            Pair(
                payloadMessage.copy(
                    payload = withContext(Dispatchers.IO) {
                        httpClient.postPayloadRequest(payloadRequest).processedPayload!!
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
            val payloadError = runCatching { clientRequestException.response.body<PayloadResponse>().error }.getOrNull()
            val errorMsg = "Processing has failed${payloadError?.let { ": ${it.descriptionText} [${it.code.value}]" } ?: ""}"
            when (clientRequestException.response.status) {
                HttpStatusCode.BadRequest -> {
                    return Pair(
                        payloadMessage.convertToErrorActionMessage(
                            clientRequestException.retrieveReturnableApprecResponse(direction, errorMsg).processedPayload!!,
                            payloadProcessing.processConfig.errorAction!!
                        ),
                        Direction.OUT
                    )
                }

                else -> throw EbmsException(errorMsg, exception = clientRequestException)
            }
        } catch (exception: Exception) {
            throw EbmsException("Processing has failed", exception = exception)
        }
    }

    private suspend fun ClientRequestException.retrieveReturnableApprecResponse(
        direction: Direction,
        errorMsg: String
    ): PayloadResponse = withContext(Dispatchers.IO) {
        this@retrieveReturnableApprecResponse.response.body<PayloadResponse?>().takeIf { payloadResponse ->
            payloadResponse != null &&
                payloadResponse.apprec &&
                payloadResponse.processedPayload != null &&
                direction == Direction.IN
        } ?: throw EbmsException(errorMsg, exception = this@retrieveReturnableApprecResponse)
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

    suspend fun processAsync(payloadMessage: PayloadMessage, payloadProcessing: PayloadProcessing?): Pair<PayloadMessage, Direction> {
        if (payloadProcessing == null) throw Exception("Processing information is missing for ${payloadMessage.messageId}")
        return processMessage(payloadMessage, payloadProcessing, Direction.IN, payloadMessage.addressing)
    }
}

private fun PayloadProcessing.hasActionableProcessingSteps(): Boolean =
    this.processConfig.signering ||
        this.processConfig.kryptering ||
        this.processConfig.komprimering ||
        this.processConfig.juridiskLogg

private fun PayloadMessage.convertToErrorActionMessage(payload: Payload, errorAction: String): PayloadMessage =
    this.copy(
        payload = payload,
        addressing = this.addressing.copy(
            action = errorAction,
            to = this.addressing.from,
            from = this.addressing.to
        )
    )
