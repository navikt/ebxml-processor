package no.nav.emottak.ebms.processing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.ebms.configuration.config
import no.nav.emottak.ebms.log
import no.nav.emottak.ebms.messaging.EbmsSignalProducer
import no.nav.emottak.ebms.model.saveEbmsMessage
import no.nav.emottak.ebms.model.saveEvent
import no.nav.emottak.ebms.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.ebms.persistence.repository.EventsRepository
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
    val ebmsMessageDetailsRepository: EbmsMessageDetailsRepository,
    val eventsRepository: EventsRepository,
    val validator: DokumentValidator,
    val processingService: ProcessingService,
    val ebmsSignalProducer: EbmsSignalProducer
) {

    suspend fun process(reference: String, content: ByteArray) {
        val ebmsPayloadMessage = createEbmsDocument(reference, content)
        log.info(ebmsPayloadMessage.marker(), "Got payload message with reference <$reference>")
        ebmsMessageDetailsRepository.saveEbmsMessage(ebmsPayloadMessage) // TODO Duplicate check
        processPayloadMessage(ebmsPayloadMessage)
    }

    private suspend fun createEbmsDocument(
        reference: String,
        content: ByteArray
    ): PayloadMessage {
        val ebmsMessage = EbMSDocument(
            reference,
            withContext(Dispatchers.IO) {
                getDocumentBuilder().parse(ByteArrayInputStream(content))
            },
            retrievePayloads(reference)
        ).transform().takeIf { it is PayloadMessage } ?: throw RuntimeException("Cannot process message as payload message: $reference")
        return ebmsMessage as PayloadMessage
    }

    private fun retrievePayloads(reference: String): List<Payload> {
        // TODO get actual payloads from smtp-transport with reference
        return listOf(
            Payload(byteArrayOf(), "application/xml", "contentId")
        )
    }

    private suspend fun processPayloadMessage(ebmsPayloadMessage: PayloadMessage) {
        ebmsPayloadMessage.saveEvent("Message received", eventsRepository)

        var signalResponderEmails = emptyList<EmailAddress>()
        try {
            validator
                .validateIn(ebmsPayloadMessage)
                .also {
                    signalResponderEmails = it.signalEmailAddress
                    processingService.processAsync(ebmsPayloadMessage, it.payloadProcessing)
                    // TODO store events from processing (juridisklog ++)
                    // TODO send to fagsystem
                }
            ebmsPayloadMessage
                .createAcknowledgment()
                .also {
                    ebmsMessageDetailsRepository.saveEbmsMessage(it)
                }
                .toEbmsDokument()
                .also {
                    // TODO sign acknowledgment
                    sendResponseToTopic(it, signalResponderEmails)
                }
        } catch (ebmsException: EbmsException) {
            ebmsPayloadMessage
                .createFail(ebmsException.feil)
                .also {
                    ebmsMessageDetailsRepository.saveEbmsMessage(it)
                }
                .toEbmsDokument()
                .also {
                    // TODO sign MessageError
                    sendResponseToTopic(it, signalResponderEmails)
                }
        } catch (ex: Exception) {
            log.error(ebmsPayloadMessage.marker(), "Unknown error during message processing: ${ex.message}", ex)
            // TODO Send to error topic?
            throw ex
        }
    }

    private suspend fun sendResponseToTopic(ebMSDocument: EbMSDocument, signalResponderEmails: List<EmailAddress>) {
        if (config().kafkaSignalProducer.active) {
            val markers = ebMSDocument.messageHeader().marker()
            try {
                log.info(markers, "Sending message to Kafka queue")
                ebmsSignalProducer.send(ebMSDocument.requestId, ebMSDocument.dokument.asByteArray())
            } catch (e: Exception) {
                log.error(markers, "Exception occurred while sending message to Kafka queue", e)
            }
        }
    }
}
