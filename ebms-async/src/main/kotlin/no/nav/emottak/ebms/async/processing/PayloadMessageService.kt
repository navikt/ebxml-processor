package no.nav.emottak.ebms.async.processing

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.consumer.failedMessageQueue
import no.nav.emottak.ebms.async.kafka.producer.EbmsMessageProducer
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.async.util.toKafkaHeaders
import no.nav.emottak.ebms.eventmanager.EventManagerService
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.message.model.EbMSDocument
import no.nav.emottak.message.model.EmailAddress
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.xml.asByteArray
import no.nav.emottak.util.marker
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.PerMessageCharacteristicsType

class PayloadMessageService(
    val cpaValidationService: CPAValidationService,
    val processingService: ProcessingService,
    val ebmsSignalProducer: EbmsMessageProducer,
    val payloadMessageForwardingService: PayloadMessageForwardingService,
    val eventRegistrationService: EventRegistrationService,
    val eventManagerService: EventManagerService
) {

    suspend fun process(
        record: ReceiverRecord<String, ByteArray>,
        ebmsPayloadMessage: PayloadMessage
    ) {
        try {
            processPayloadMessage(ebmsPayloadMessage)
            returnAcknowledgment(ebmsPayloadMessage)
        } catch (e: EbmsException) {
            try {
                returnMessageError(ebmsPayloadMessage, e)
            } catch (ex: Exception) {
                log.error(ebmsPayloadMessage.marker(), "Failed to return MessageError", ex)
                failedMessageQueue.sendToRetry(
                    record = record,
                    reason = "Failed to return MessageError: ${ex.message ?: "Unknown error"}"
                )
            }
        } catch (ex: Exception) {
            log.error(ebmsPayloadMessage.marker(), ex.message ?: "Unknown error", ex)
            failedMessageQueue.sendToRetry(
                record = record,
                reason = ex.message ?: "Unknown error"
            )
            throw ex
        }
    }

    suspend fun processPayloadMessage(
        ebmsPayloadMessage: PayloadMessage
    ) {
        eventRegistrationService.registerEvent(
            eventType = EventType.MESSAGE_READ_FROM_QUEUE,
            payloadMessage = ebmsPayloadMessage,
            eventData = Json.encodeToString(
                mapOf(EventDataType.QUEUE_NAME.value to config().kafkaPayloadReceiver.topic)
            )
        )

        if (isDuplicateMessage(ebmsPayloadMessage)) {
            log.info(ebmsPayloadMessage.marker(), "Got duplicate payload message with reference <${ebmsPayloadMessage.requestId}>")
        } else {
            log.info(ebmsPayloadMessage.marker(), "Got payload message with reference <${ebmsPayloadMessage.requestId}>")
            cpaValidationService
                .validateIncomingMessage(ebmsPayloadMessage)
                .let {
                    processingService.processAsync(ebmsPayloadMessage, it.payloadProcessing)
                }
                .let {
                    when (val service = it.addressing.service) {
                        "HarBorgerFrikortMengde", "Inntektsforesporsel" -> {
                            log.debug(it.marker(), "Starting SendIn for $service")
                            payloadMessageForwardingService.forwardMessageWithSyncResponse(it)
                        }
                        else -> log.debug(it.marker(), "Skipping SendIn for $service")
                    }
                }
        }
    }

    private suspend fun returnAcknowledgment(ebmsPayloadMessage: PayloadMessage) {
        ebmsPayloadMessage
            .createAcknowledgment()
            .also {
                val validationResult = cpaValidationService.validateOutgoingMessage(it)
                sendResponseToTopic(
                    it.toEbmsDokument().signer(validationResult.payloadProcessing!!.signingCertificate),
                    validationResult.receiverEmailAddress
                )
                log.info(it.marker(), "Acknowledgment returned")
            }
    }

    private suspend fun returnMessageError(ebmsPayloadMessage: PayloadMessage, ebmsException: EbmsException) {
        ebmsPayloadMessage
            .createMessageError(ebmsException.feil)
            .also {
                val validationResult = cpaValidationService.validateOutgoingMessage(it)
                val signingCertificate = validationResult.payloadProcessing?.signingCertificate
                if (signingCertificate == null) {
                    log.warn(it.marker(), "Could not find signing certificate for outgoing MessageError")
                } else {
                    sendResponseToTopic(
                        it.toEbmsDokument().signer(signingCertificate),
                        validationResult.signalEmailAddress
                    )
                    log.warn(it.marker(), "MessageError returned", ebmsException)
                }
            }
    }

    private suspend fun sendResponseToTopic(ebMSDocument: EbMSDocument, signalResponderEmails: List<EmailAddress>) {
        if (config().kafkaSignalProducer.active) {
            val messageHeader = ebMSDocument.messageHeader()
            try {
                log.info(messageHeader.marker(), "Sending message to Kafka queue")
                ebmsSignalProducer.publishMessage(
                    key = ebMSDocument.requestId,
                    value = ebMSDocument.dokument.asByteArray(),
                    headers = signalResponderEmails.toKafkaHeaders() + messageHeader.toKafkaHeaders()
                )
            } catch (e: Exception) {
                log.error(messageHeader.marker(), "Exception occurred while sending message to Kafka queue", e)
            }
        }
    }

    private suspend fun isDuplicateMessage(ebmsPayloadMessage: PayloadMessage): Boolean {
        val duplicateEliminationStrategy = cpaValidationService.getDuplicateEliminationStrategy(ebmsPayloadMessage)

        if (duplicateEliminationStrategy == PerMessageCharacteristicsType.ALWAYS) {
            return eventManagerService.isDuplicateMessage(ebmsPayloadMessage)
        }

        if (
            duplicateEliminationStrategy == PerMessageCharacteristicsType.PER_MESSAGE &&
            ebmsPayloadMessage.duplicateElimination
        ) {
            return eventManagerService.isDuplicateMessage(ebmsPayloadMessage)
        }
        return false
    }
}
