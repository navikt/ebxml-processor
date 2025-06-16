package no.nav.emottak.ebms.async.processing

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.ebms.SmtpTransportClient
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.consumer.failedMessageQueue
import no.nav.emottak.ebms.async.kafka.producer.EbmsMessageProducer
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.message.model.EbMSDocument
import no.nav.emottak.message.model.EmailAddress
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.xml.asByteArray
import no.nav.emottak.message.xml.getDocumentBuilder
import no.nav.emottak.util.marker
import java.io.ByteArrayInputStream

class PayloadMessageProcessor(
    // val ebmsMessageDetailsRepository: EbmsMessageDetailsRepository,
    val validator: DokumentValidator,
    val processingService: ProcessingService,
    val ebmsSignalProducer: EbmsMessageProducer,
    val smtpTransportClient: SmtpTransportClient,
    val payloadMessageResponder: PayloadMessageResponder
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
        ).transform().takeIf { it is PayloadMessage } ?: throw RuntimeException("Cannot process message as payload message: $requestId")
        return ebmsMessage as PayloadMessage
    }

    private suspend fun retrievePayloads(reference: String) =
        smtpTransportClient.getPayload(reference).map {
            Payload(
                bytes = it.content,
                contentId = it.contentId,
                contentType = it.contentType
            )
        }

    private suspend fun processPayloadMessage(
        ebmsPayloadMessage: PayloadMessage,
        record: ReceiverRecord<String, ByteArray>
    ) {
        try {
           /*
           if (isDuplicateMessage(ebmsPayloadMessage)) {
                log.info(ebmsPayloadMessage.marker(), "Got duplicate payload message with reference <${record.key()}>")
            } else {

            */
            log.info(ebmsPayloadMessage.marker(), "Got payload message with reference <${record.key()}>")
            // ebmsMessageDetailsRepository.saveEbmsMessage(ebmsPayloadMessage)
            // eventsRepository.saveEvent("Message received", ebmsPayloadMessage)
            validator
                .validateIn(ebmsPayloadMessage)
                .let {
                    processingService.processAsync(ebmsPayloadMessage, it.payloadProcessing)
                    // TODO store events from processing (juridisklog ++)
                }
                .let {
                    // TODO do this asynchronously
                    when (val service = it.addressing.service) {
                        "HarBorgerFrikortMengde" -> {
                            log.debug(it.marker(), "Starting SendIn for $service")
                            payloadMessageResponder.respond(it)
                        }
                        else -> {
                            log.debug(it.marker(), "Skipping SendIn for $service")
                        }
                    }
                }
            // }
            returnAcknowledgment(ebmsPayloadMessage)
        } catch (e: EbmsException) {
            returnMessageError(ebmsPayloadMessage, e)
        } catch (ex: Exception) {
            log.error(ebmsPayloadMessage.marker(), ex.message ?: "Unknown error", ex)
            failedMessageQueue.sendToRetry(
                record,
                ebmsPayloadMessage.requestId,
                ebmsPayloadMessage.toEbmsDokument().dokument.asByteArray(),
                ex.message ?: "Unknown error"
            )
            throw ex
        }
    }

    // TODO More advanced duplicate check
    /*private fun isDuplicateMessage(ebmsPayloadMessage: PayloadMessage): Boolean {
        log.debug(ebmsPayloadMessage.marker(), "Checking for duplicates")
        return ebmsMessageDetailsRepository.getByConversationIdMessageIdAndCpaId(
            conversationId = ebmsPayloadMessage.conversationId,
            messageId = ebmsPayloadMessage.messageId,
            cpaId = ebmsPayloadMessage.cpaId
        ) != null
    }
*/
    private suspend fun returnAcknowledgment(ebmsPayloadMessage: PayloadMessage) {
        ebmsPayloadMessage
            .createAcknowledgment()
            .also {
                val validationResult = validator.validateOut(it)
                // ebmsMessageDetailsRepository.saveEbmsMessage(it)
                sendResponseToTopic(
                    it.toEbmsDokument().signer(validationResult.payloadProcessing!!.signingCertificate),
                    validationResult.receiverEmailAddress
                )
                log.info(it.marker(), "Acknowledgment returned")
            }
    }

    private suspend fun returnMessageError(ebmsPayloadMessage: PayloadMessage, ebmsException: EbmsException) {
        ebmsPayloadMessage
            .createFail(ebmsException.feil)
            .also {
                val validationResult = validator.validateOut(it)
                // ebmsMessageDetailsRepository.saveEbmsMessage(it)
                sendResponseToTopic(
                    it.toEbmsDokument().signer(validationResult.payloadProcessing!!.signingCertificate),
                    validationResult.receiverEmailAddress
                )
                log.warn(it.marker(), "MessageError returned")
            }
    }

    private suspend fun sendResponseToTopic(ebMSDocument: EbMSDocument, signalResponderEmails: List<EmailAddress>) {
        if (config().kafkaSignalProducer.active) {
            val markers = ebMSDocument.messageHeader().marker()
            try {
                log.info(markers, "Sending message to Kafka queue")
                ebmsSignalProducer.publishMessage(ebMSDocument.requestId, ebMSDocument.dokument.asByteArray())
            } catch (e: Exception) {
                log.error(markers, "Exception occurred while sending message to Kafka queue", e)
            }
        }
    }
}
