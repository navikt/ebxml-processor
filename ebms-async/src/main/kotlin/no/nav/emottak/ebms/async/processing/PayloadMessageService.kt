package no.nav.emottak.ebms.async.processing

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandler
import no.nav.emottak.ebms.async.kafka.consumer.retryCount
import no.nav.emottak.ebms.async.kafka.producer.EbmsMessageProducer
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.async.util.toKafkaHeaders
import no.nav.emottak.ebms.eventmanager.EventManagerService
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.util.toByteArray
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.EmailAddress
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.util.marker
import no.nav.emottak.util.signatur.SignatureException
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.PerMessageCharacteristicsType

class PayloadMessageService(
    val cpaValidationService: CPAValidationService,
    val processingService: ProcessingService,
    val ebmsSignalProducer: EbmsMessageProducer,
    val payloadMessageForwardingService: PayloadMessageForwardingService,
    val eventRegistrationService: EventRegistrationService,
    val eventManagerService: EventManagerService,
    val failedMessageQueue: FailedMessageKafkaHandler
) {

    suspend fun process(
        record: ReceiverRecord<String, ByteArray>,
        ebmsPayloadMessage: PayloadMessage
    ) {
        runCatching {
            when (isDuplicateMessage(ebmsPayloadMessage)) {
                true -> log.info(ebmsPayloadMessage.marker(), "Got duplicate payload message with reference <${ebmsPayloadMessage.requestId}>")
                false -> processPayloadMessage(ebmsPayloadMessage)
            }
            returnAcknowledgment(ebmsPayloadMessage)
        }.onFailure { exception ->
            when (exception) {
                is EbmsException -> {
                    runCatching {
                        returnMessageError(ebmsPayloadMessage, exception)
                    }.onFailure {
                        log.error(ebmsPayloadMessage.marker(), "Failed to return MessageError", exception)
                        // TODO her antar vi at vi ALDRI skal gi opp, så denne sendes til retry uansett hvor mange ganger den er rekjørt
                        sendToRetry(record = record, exceptionReason = "Failed to return MessageError: ${exception.message ?: "Unknown error"}")
                    }
                }
                is SignatureException -> {
                    log.error(ebmsPayloadMessage.marker(), exception.message, exception)
                    // TODO her sjekker vi om denne er rekjørt max antall ganger,
                    // isåfall lager vi en EbmsException og sender MessageError tilbake, som i blokka ovenfor
                    sendToRetry(record = record, exceptionReason = exception.message)
                }
                else -> {
                    log.error(ebmsPayloadMessage.marker(), exception.message ?: "Unknown error", exception)
                    // TODO 1 her sjekker vi om denne er rekjørt max antall ganger,
                    // isåfall lager vi en EbmsException og sender MessageError tilbake, som i blokka ovenfor
                    // TODO 2 antar at vi ikke skal kaste exception på nytt ved rekjøring, derfor sjekker vi om den er rekjørt
                    val retriedAlready = record.retryCount()
                    sendToRetry(record = record, exceptionReason = exception.message ?: "Unknown error")
                    if (retriedAlready == 0) {
                        throw exception
                    }
                }
            }
        }
    }

    private suspend fun processPayloadMessage(ebmsPayloadMessage: PayloadMessage) {
        log.info(ebmsPayloadMessage.marker(), "Got payload message with reference <${ebmsPayloadMessage.requestId}>")
        eventRegistrationService.registerEventMessageDetails(ebmsPayloadMessage)
        val validationResult = cpaValidationService.validateIncomingMessage(ebmsPayloadMessage)
        val (processedPayload, direction) = processingService.processAsync(ebmsPayloadMessage, validationResult.payloadProcessing)
        when (direction) {
            Direction.IN -> payloadMessageForwardingService.forwardMessageWithSyncResponse(processedPayload)
            Direction.OUT -> payloadMessageForwardingService.returnMessageResponse(processedPayload)
        }
    }

    private suspend fun returnAcknowledgment(ebmsPayloadMessage: PayloadMessage) {
        val acknowledgment = ebmsPayloadMessage.createAcknowledgment().also {
            eventRegistrationService.registerEventMessageDetails(it)
        }
        val validationResult = cpaValidationService.validateOutgoingMessage(acknowledgment)
        sendResponseToTopic(
            acknowledgment.toEbmsDokument().signer(validationResult.payloadProcessing!!.signingCertificate),
            validationResult.signalEmailAddress
        )
        log.info(acknowledgment.marker(), "Acknowledgment returned")
    }

    private suspend fun returnMessageError(ebmsPayloadMessage: PayloadMessage, ebmsException: EbmsException) {
        val messageError = ebmsPayloadMessage.createMessageError(ebmsException.feil).also {
            eventRegistrationService.registerEventMessageDetails(it)
        }
        val validationResult = cpaValidationService.validateOutgoingMessage(messageError)
        val signingCertificate = validationResult.payloadProcessing?.signingCertificate
        if (signingCertificate == null) {
            log.warn(messageError.marker(), "Could not find signing certificate for outgoing MessageError")
        } else {
            sendResponseToTopic(
                messageError.toEbmsDokument().signer(signingCertificate),
                validationResult.signalEmailAddress
            )
            log.warn(messageError.marker(), "MessageError returned", ebmsException)
        }
    }

    private suspend fun sendResponseToTopic(ebmsDocument: EbmsDocument, signalResponderEmails: List<EmailAddress>) {
        if (config().kafkaSignalProducer.active) {
            val messageHeader = ebmsDocument.messageHeader()
            try {
                log.info(messageHeader.marker(), "Sending message to Kafka queue")
                eventRegistrationService.runWithEvent(
                    successEvent = EventType.MESSAGE_PLACED_IN_QUEUE,
                    failEvent = EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
                    requestId = ebmsDocument.requestId.parseOrGenerateUuid(),
                    messageId = ebmsDocument.messageHeader().messageData.messageId ?: "",
                    eventData = Json.encodeToString(
                        mapOf(EventDataType.QUEUE_NAME.value to config().kafkaSignalProducer.topic)
                    )
                ) {
                    ebmsSignalProducer.publishMessage(
                        key = ebmsDocument.requestId,
                        value = ebmsDocument.document.toByteArray(),
                        headers = signalResponderEmails.toKafkaHeaders() + messageHeader.toKafkaHeaders()
                    )
                }
            } catch (e: Exception) {
                log.error(messageHeader.marker(), "Exception occurred while sending message to Kafka queue", e)
            }
        }
    }

    private suspend fun sendToRetry(record: ReceiverRecord<String, ByteArray>, exceptionReason: String) {
        if (config().kafkaSignalProducer.active && config().kafkaPayloadProducer.active && config().kafkaErrorQueue.active) {
            failedMessageQueue.sendToRetry(
                record = record,
                reason = exceptionReason
            )
        }
    }

    suspend fun isDuplicateMessage(ebmsPayloadMessage: PayloadMessage): Boolean {
        val duplicateEliminationStrategy = try {
            cpaValidationService.getDuplicateEliminationStrategy(ebmsPayloadMessage)
        } catch (e: Exception) {
            log.warn(ebmsPayloadMessage.marker(), "Error checking duplicate status", e)
            return false
        }

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
