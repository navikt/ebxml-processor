package no.nav.emottak.ebms.async.processing

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import no.nav.emottak.ebms.async.kafka.consumer.retryCount
import no.nav.emottak.ebms.async.kafka.producer.EbmsMessageProducer
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.eventmanager.EventManagerService
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.ValidationResult
import no.nav.emottak.util.marker
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.EventType
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.PerMessageCharacteristicsType

class PayloadMessageService(
    val cpaValidationService: CPAValidationService,
    val processingService: ProcessingService,
    val ebmsSignalProducer: EbmsMessageProducer,
    val payloadMessageForwardingService: PayloadMessageForwardingService,
    val eventRegistrationService: EventRegistrationService,
    val eventManagerService: EventManagerService,
    val retryService: RetryService,
    val useAsyncInbound: Boolean
) {

    suspend fun process(
        record: ReceiverRecord<String, ByteArray>,
        ebmsPayloadMessage: PayloadMessage
    ) {
        runCatching {
            val isDuplicate = isDuplicateMessage(ebmsPayloadMessage)
            val isRetry = record.retryCount() > 0
            when (isDuplicate && !isRetry) {
                true -> log.info(ebmsPayloadMessage.marker(), "Got duplicate payload message with reference <${ebmsPayloadMessage.requestId}>")
                false -> {
                    if (isRetry) {
                        eventRegistrationService.registerEvent(
                            eventType = EventType.RETRY_TRIGGED,
                            requestId = ebmsPayloadMessage.requestId.parseOrGenerateUuid(),
                            messageId = ebmsPayloadMessage.messageId,
                            conversationId = ebmsPayloadMessage.conversationId
                        )
                    }
                    processPayloadMessage(ebmsPayloadMessage)
                }
            }
            returnAcknowledgment(ebmsPayloadMessage)
        }.onFailure { exception ->
            // TODO handle some errors by sending to retry, some by returning error message
            log.error(ebmsPayloadMessage.marker(), exception.message ?: "Message processing error", exception)
            retryService.incomingRetryEval(record = record, payloadMessage = ebmsPayloadMessage, exception = exception)
        }
    }

    suspend fun processOutboundResponse(
        record: ReceiverRecord<String, ByteArray>,
        ebmsPayloadMessage: PayloadMessage
    ) {
        runCatching {
            log.info(ebmsPayloadMessage.marker(), "Got outbound response message from ebms.out.payload with reference <${ebmsPayloadMessage.requestId}>")
            payloadMessageForwardingService.returnMessageResponse(ebmsPayloadMessage)
        }.onFailure { exception ->
            log.error(ebmsPayloadMessage.marker(), exception.message ?: "Outbound response processing error", exception)
            retryService.outgoingRetryEval(record = record, payloadMessage = ebmsPayloadMessage, exception = exception)
        }
    }

    private suspend fun processPayloadMessage(ebmsPayloadMessage: PayloadMessage) {
        log.info(ebmsPayloadMessage.marker(), "Got payload message with reference <${ebmsPayloadMessage.requestId}>")
        eventRegistrationService.registerEventMessageDetails(ebmsPayloadMessage)
        val validationResult = cpaValidationService.validateIncomingMessage(ebmsPayloadMessage)
        val (processedPayload, direction) = processingService.processAsync(ebmsPayloadMessage, validationResult.payloadProcessing)
        when (direction) {
            Direction.IN -> {
                routeDependingOnMessageType(processedPayload, validationResult)
            }
            Direction.OUT -> payloadMessageForwardingService.returnMessageResponse(processedPayload)
        }
    }

    private suspend fun routeDependingOnMessageType(processedPayload: PayloadMessage, validationResult: ValidationResult) {
        when (val messageType = messageTypeByServiceName(processedPayload.addressing.service)) {
            MessageType.HAR_BORGER_FRIKORT_MENGDE, MessageType.INNTEKTSFORESPORSEL -> {
                log.debug(processedPayload.marker(), "Calling SendIn SYNCHRONOUSLY for $messageType")
                payloadMessageForwardingService.forwardMessageWithSyncResponse(processedPayload)
            }
            MessageType.TREKKOPPLYSNING -> {
                if (useAsyncInbound) {
                    log.debug(processedPayload.marker(), "Calling SendIn ASYNCHRONOUSLY for $messageType")
                    payloadMessageForwardingService.forwardMessageWithAsyncResponse(processedPayload, validationResult.partnerId)
                } else {
                    log.debug(processedPayload.marker(), "Calling SendIn SYNCHRONOUSLY (due to async flag turned OFF) for $messageType")
                    payloadMessageForwardingService.forwardMessageWithSyncResponse(processedPayload)
                }
            }
            else -> {
                log.debug(processedPayload.marker(), "Skipping SendIn for $processedPayload.addressing.service")
            }
        }
    }

    private suspend fun returnAcknowledgment(ebmsPayloadMessage: PayloadMessage) {
        val acknowledgment = ebmsPayloadMessage.createAcknowledgment().also {
            eventRegistrationService.registerEventMessageDetails(it)
        }
        val validationResult = cpaValidationService.validateOutgoingMessage(acknowledgment)
        sendSignalResponseToTopic(
            ebmsSignalProducer,
            eventRegistrationService,
            acknowledgment.toEbmsDokument().signer(validationResult.payloadProcessing!!.signingCertificate),
            validationResult.signalEmailAddress
        )
        log.info(acknowledgment.marker(), "Acknowledgment returned")
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
