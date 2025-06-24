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
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.async.util.toHeaders
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
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import java.io.ByteArrayInputStream

class PayloadMessageService(
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
            retrievePayloads(requestId)
        ).transform().takeIf { it is PayloadMessage }
            ?: throw RuntimeException("Cannot process message as payload message: $requestId")
        return ebmsMessage as PayloadMessage
    }

    private suspend fun retrievePayloads(reference: String): List<Payload> {
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

            log.info(ebmsPayloadMessage.marker(), "Got payload message with reference <${record.key()}>")
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
            val markers = ebMSDocument.messageHeader().marker()
            try {
                log.info(markers, "Sending message to Kafka queue")
                ebmsSignalProducer.publishMessage(
                    ebMSDocument.requestId,
                    ebMSDocument.dokument.asByteArray(),
                    signalResponderEmails.toHeaders()

                )
            } catch (e: Exception) {
                log.error(markers, "Exception occurred while sending message to Kafka queue", e)
            }
        }
    }
}
