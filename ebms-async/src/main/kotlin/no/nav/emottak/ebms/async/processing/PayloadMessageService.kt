package no.nav.emottak.ebms.async.processing

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.SmtpTransportClient
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.consumer.failedMessageQueue
import no.nav.emottak.ebms.async.kafka.producer.EbmsMessageProducer
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.async.util.toKafkaHeaders
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.message.model.EbMSDocument
import no.nav.emottak.message.model.EmailAddress
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.xml.asByteArray
import no.nav.emottak.message.xml.getDocumentBuilder
import no.nav.emottak.util.marker
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import java.io.ByteArrayInputStream
import kotlin.uuid.Uuid

class PayloadMessageService(
    val ebmsMessageDetailsRepository: EbmsMessageDetailsRepository,
    val cpaValidationService: CPAValidationService,
    val processingService: ProcessingService,
    val ebmsSignalProducer: EbmsMessageProducer,
    val smtpTransportClient: SmtpTransportClient,
    val payloadMessageForwardingService: PayloadMessageForwardingService,
    val eventRegistrationService: EventRegistrationService
) {
    suspend fun process(record: ReceiverRecord<String, ByteArray>) {
        try {
            processPayloadMessage(createEbmsDocument(record.key(), record.value()), record)
        } catch (e: Exception) {
            log.error("Message failed for reference ${record.key()}", e)
        }
    }

    private suspend fun createEbmsDocument(
        requestId: String,
        content: ByteArray
    ): PayloadMessage {
        val ebmsMessage = EbMSDocument(
            requestId,
            withContext(Dispatchers.IO) {
                getDocumentBuilder().parse(ByteArrayInputStream(content))
            },
            retrievePayloads(requestId.parseOrGenerateUuid())
        ).transform().takeIf { it is PayloadMessage }
            ?: throw RuntimeException("Cannot process message as payload message: $requestId")
        return ebmsMessage as PayloadMessage
    }

    private suspend fun retrievePayloads(reference: Uuid): List<Payload> {
        return smtpTransportClient.getPayload(reference)
            .map {
                eventRegistrationService.runWithEvent(
                    successEvent = EventType.PAYLOAD_RECEIVED_VIA_HTTP,
                    failEvent = EventType.ERROR_WHILE_RECEIVING_PAYLOAD_VIA_HTTP,
                    requestId = reference,
                    contentId = it.contentId
                ) {
                    Payload(
                        bytes = it.content,
                        contentId = it.contentId,
                        contentType = it.contentType
                    )
                }
            }
    }

    private suspend fun processPayloadMessage(
        ebmsPayloadMessage: PayloadMessage,
        record: ReceiverRecord<String, ByteArray>
    ) {
        try {
            val eventData = Json.encodeToString(
                mapOf(EventDataType.QUEUE_NAME.value to config().kafkaPayloadReceiver.topic)
            )
            eventRegistrationService.registerEvent(
                EventType.MESSAGE_READ_FROM_QUEUE,
                ebmsPayloadMessage,
                eventData
            )

            if (isDuplicateMessage(ebmsPayloadMessage)) {
                log.info(ebmsPayloadMessage.marker(), "Got duplicate payload message with reference <${record.key()}>")
            } else {
                log.info(ebmsPayloadMessage.marker(), "Got payload message with reference <${record.key()}>")
                ebmsMessageDetailsRepository.saveEbmsMessage(ebmsPayloadMessage)
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

                            else -> {
                                log.debug(it.marker(), "Skipping SendIn for $service")
                            }
                        }
                    }
            }
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

    // TODO More advanced duplicate check
    private fun isDuplicateMessage(ebmsPayloadMessage: PayloadMessage): Boolean {
        log.debug(ebmsPayloadMessage.marker(), "Checking for duplicates")
        return ebmsMessageDetailsRepository.getByConversationIdMessageIdAndCpaId(
            conversationId = ebmsPayloadMessage.conversationId,
            messageId = ebmsPayloadMessage.messageId,
            cpaId = ebmsPayloadMessage.cpaId
        ) != null
    }

    private suspend fun returnAcknowledgment(ebmsPayloadMessage: PayloadMessage) {
        ebmsPayloadMessage
            .createAcknowledgment()
            .also {
                val validationResult = cpaValidationService.validateOutgoingMessage(it)
                ebmsMessageDetailsRepository.saveEbmsMessage(it)
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
                ebmsMessageDetailsRepository.saveEbmsMessage(it)
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
}
