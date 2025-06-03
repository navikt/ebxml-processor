package no.nav.emottak.ebms.async.processing

import io.ktor.http.ContentType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.producer.EbmsMessageProducer
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.ebms.async.persistence.repository.PayloadRepository
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.model.AsyncPayload
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.xml.asByteArray
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PayloadMessageResponder(
    val sendInService: SendInService,
    val cpaValidationService: CPAValidationService,
    val processingService: ProcessingService,
    val payloadRepository: PayloadRepository,
    val ebmsMessageDetailsRepository: EbmsMessageDetailsRepository,
    val ebmsPayloadProducer: EbmsMessageProducer,
    val eventRegistrationService: EventRegistrationService
) {

    @OptIn(ExperimentalUuidApi::class)
    suspend fun respond(payloadMessage: PayloadMessage) {
        try {
            sendInService.sendIn(payloadMessage).let { sendInResponse ->
                PayloadMessage(
                    requestId = Uuid.random().toString(),
                    messageId = Uuid.random().toString(),
                    conversationId = sendInResponse.conversationId,
                    cpaId = payloadMessage.cpaId,
                    addressing = sendInResponse.addressing,
                    payload = Payload(sendInResponse.payload, ContentType.Application.Xml.toString()),
                    refToMessageId = payloadMessage.messageId
                )
            }.let { payloadMessageResponse ->
                Pair(payloadMessageResponse, cpaValidationService.validateOutgoingMessage(payloadMessageResponse).payloadProcessing)
            }.let { messageProcessing ->
                val processedMessage =
                    processingService.proccessSyncOut(messageProcessing.first, messageProcessing.second)
                Pair(processedMessage, messageProcessing.second)
            }.let {
                it.first.also {
                    ebmsMessageDetailsRepository.saveEbmsMessage(it)
                }.toEbmsDokument().signer(it.second!!.signingCertificate)
                    .let {
                        it.attachments.forEach { payload ->
                            payloadRepository.updateOrInsert(
                                AsyncPayload(
                                    referenceId = it.requestId,
                                    contentId = payload.contentId,
                                    contentType = payload.contentType,
                                    content = payload.bytes
                                )
                            )
                        }
                        ebmsPayloadProducer.publishMessage(it.requestId, it.dokument.asByteArray()).onSuccess {
                            val eventData = Json.encodeToString(
                                mapOf(EventDataType.QUEUE_NAME to config().kafkaPayloadProducer.topic)
                            )
                            eventRegistrationService.registerEvent(
                                EventType.MESSAGE_PLACED_IN_QUEUE,
                                payloadMessage,
                                eventData
                            )
                        }.onFailure {
                            val eventData = Json.encodeToString(
                                mapOf(
                                    EventDataType.QUEUE_NAME to config().kafkaPayloadProducer.topic,
                                    EventDataType.ERROR_MESSAGE to it.message
                                )
                            )
                            eventRegistrationService.registerEvent(
                                EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
                                payloadMessage,
                                eventData
                            )
                        }
                    }
                log.info(it.first.marker(), "Payload message response returned successfully")
            }
        } catch (e: Exception) {
            log.error(payloadMessage.marker(), "Error processing asynchronous payload response", e)

            val eventData = Json.encodeToString(
                mapOf(
                    EventDataType.QUEUE_NAME to config().kafkaPayloadProducer.topic,
                    EventDataType.ERROR_MESSAGE to e.message
                )
            )
            eventRegistrationService.registerEvent(
                EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
                payloadMessage,
                eventData
            )
        }
    }
}
