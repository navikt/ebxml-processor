package no.nav.emottak.ebms.processing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.ebms.log
import no.nav.emottak.ebms.persistence.EbmsMessageRepository
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.message.model.Acknowledgment
import no.nav.emottak.message.model.EbMSDocument
import no.nav.emottak.message.model.EbmsFail
import no.nav.emottak.message.model.toEbmsMessageDetails
import no.nav.emottak.message.xml.getDocumentBuilder
import java.io.ByteArrayInputStream

class SignalProcessor(
    val ebmsMessageRepository: EbmsMessageRepository,
    val validator: DokumentValidator
) {

    suspend fun processSignal(reference: String, content: ByteArray) {
        val ebxmlSignalMessage = EbMSDocument(
            reference,
            withContext(Dispatchers.IO) {
                getDocumentBuilder().parse(ByteArrayInputStream(content))
            },
            emptyList()
        ).transform()
        validator.validateIn(ebxmlSignalMessage)
        when (ebxmlSignalMessage) {
            is Acknowledgment -> {
                ebmsMessageRepository.saveEbmsMessageDetails(ebxmlSignalMessage.toEbmsMessageDetails())
                processAcknowledgment(reference, ebxmlSignalMessage)
            }
            is EbmsFail -> {
                ebmsMessageRepository.saveEbmsMessageDetails(ebxmlSignalMessage.toEbmsMessageDetails())
                processMessageError(reference, ebxmlSignalMessage)
            }
            else -> log.warn(ebxmlSignalMessage.marker(), "Cannot process message as signal message: $reference")
        }
    }

    private fun processAcknowledgment(reference: String, acknowledgment: Acknowledgment) {
        log.info(acknowledgment.marker(), "Got acknowledgment with reference <$reference>")
        val referencedMessage = ebmsMessageRepository.getByMessageIdAndCpaId(acknowledgment.refToMessageId, acknowledgment.cpaId)
        if (referencedMessage == null) {
            log.warn(acknowledgment.marker(), "Received Acknowledgment for unknown message ${acknowledgment.refToMessageId}")
        } else {
            log.info(acknowledgment.marker(), "Message acknowledged")
        }
    }

    private fun processMessageError(reference: String, ebmsFail: EbmsFail) {
        log.info(ebmsFail.marker(), "Got MessageError with reference <$reference>")
        val messageRef = ebmsFail.refToMessageId
        if (messageRef == null) {
            log.warn(ebmsFail.marker(), "Received MessageError without message reference")
        } else {
            val referencedMessage = ebmsMessageRepository.getByMessageIdAndCpaId(messageRef, ebmsFail.cpaId)
            if (referencedMessage == null) {
                log.warn(ebmsFail.marker(), "Received MessageError for unknown message $messageRef")
            } else {
                log.info(ebmsFail.marker(), "Message Error received")
            }
            ebmsFail.feil.forEach { error ->
                log.info(ebmsFail.marker(), "Code: ${error.code}, Description: ${error.descriptionText}")
            }
        }
    }
}
