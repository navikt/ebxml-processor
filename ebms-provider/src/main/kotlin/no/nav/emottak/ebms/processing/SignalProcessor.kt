package no.nav.emottak.ebms.processing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.ebms.log
import no.nav.emottak.ebms.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.message.model.Acknowledgment
import no.nav.emottak.message.model.EbMSDocument
import no.nav.emottak.message.model.EbmsFail
import no.nav.emottak.message.model.toEbmsMessageDetails
import no.nav.emottak.message.xml.getDocumentBuilder
import java.io.ByteArrayInputStream
import kotlin.uuid.ExperimentalUuidApi

class SignalProcessor(
    val ebmsMessageDetailsRepository: EbmsMessageDetailsRepository,
    val validator: DokumentValidator
) {

    @OptIn(ExperimentalUuidApi::class)
    suspend fun processSignal(reference: String, content: ByteArray) {
        try {
            val ebxmlSignalMessage = createEbmsMessage(reference, content)
            validator.validateIn(ebxmlSignalMessage)
            when (ebxmlSignalMessage) {
                is Acknowledgment -> {
                    ebmsMessageDetailsRepository.saveEbmsMessageDetails(ebxmlSignalMessage.toEbmsMessageDetails())
                    processAcknowledgment(ebxmlSignalMessage)
                }
                is EbmsFail -> {
                    ebmsMessageDetailsRepository.saveEbmsMessageDetails(ebxmlSignalMessage.toEbmsMessageDetails())
                    processMessageError(ebxmlSignalMessage)
                }
                else -> log.warn(ebxmlSignalMessage.marker(), "Cannot process message as signal message: $reference")
            }
        } catch (e: Exception) {
            // TODO Clearer error handling
            log.error("Error processing signal reference $reference", e)
        }
    }

    private suspend fun createEbmsMessage(
        reference: String,
        content: ByteArray
    ) = EbMSDocument(
        reference,
        withContext(Dispatchers.IO) {
            getDocumentBuilder().parse(ByteArrayInputStream(content))
        },
        emptyList()
    ).transform()

    private fun processAcknowledgment(acknowledgment: Acknowledgment) {
        log.info(acknowledgment.marker(), "Got acknowledgment with reference <${acknowledgment.requestId}>")
        val referencedMessage = ebmsMessageDetailsRepository.getByConversationIdMessageIdAndCpaId(
            acknowledgment.conversationId,
            acknowledgment.refToMessageId,
            acknowledgment.cpaId
        )
        if (referencedMessage == null) {
            log.warn(acknowledgment.marker(), "Received Acknowledgment for unknown message ${acknowledgment.refToMessageId}")
        } else {
            log.info(acknowledgment.marker(), "Message acknowledged")
        }
    }

    private fun processMessageError(ebmsFail: EbmsFail) {
        log.info(ebmsFail.marker(), "Got MessageError with reference <${ebmsFail.requestId}>")
        val messageRef = ebmsFail.refToMessageId
        if (messageRef == null) {
            log.warn(ebmsFail.marker(), "Received MessageError without message reference")
            return
        }

        val referencedMessage = ebmsMessageDetailsRepository.getByConversationIdMessageIdAndCpaId(
            ebmsFail.conversationId,
            messageRef,
            ebmsFail.cpaId
        )
        if (referencedMessage == null) {
            log.warn(ebmsFail.marker(), "Received MessageError for unknown message $messageRef")
        } else {
            log.info(ebmsFail.marker(), "Message Error received")
        }
        // TODO store events
        ebmsFail.feil.forEach { error ->
            log.info(ebmsFail.marker(), "Code: ${error.code}, Description: ${error.descriptionText}")
        }
    }
}
