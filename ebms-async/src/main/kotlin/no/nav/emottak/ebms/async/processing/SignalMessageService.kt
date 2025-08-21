package no.nav.emottak.ebms.async.processing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.model.Acknowledgment
import no.nav.emottak.message.model.EbMSDocument
import no.nav.emottak.message.model.MessageError
import no.nav.emottak.message.xml.getDocumentBuilder
import no.nav.emottak.util.marker
import java.io.ByteArrayInputStream

class SignalMessageService(
    val cpaValidationService: CPAValidationService
) {

    suspend fun processSignal(requestId: String, content: ByteArray) {
        try {
            val ebxmlSignalMessage = createEbmsMessage(requestId, content)
            cpaValidationService.validateIncomingMessage(ebxmlSignalMessage)
            when (ebxmlSignalMessage) {
                is Acknowledgment -> {
                    processAcknowledgment(ebxmlSignalMessage)
                }
                is MessageError -> {
                    processMessageError(ebxmlSignalMessage)
                }
                else -> log.warn(ebxmlSignalMessage.marker(), "Cannot process message as signal message: $requestId")
            }
        } catch (e: Exception) {
            // TODO Clearer error handling
            log.error("Error processing signal requestId $requestId", e)
        }
    }

    private suspend fun createEbmsMessage(
        requestId: String,
        content: ByteArray
    ) = EbMSDocument(
        requestId,
        withContext(Dispatchers.IO) {
            getDocumentBuilder().parse(ByteArrayInputStream(content))
        },
        emptyList()
    ).transform()

    private fun processAcknowledgment(acknowledgment: Acknowledgment) {
        log.info(acknowledgment.marker(), "Got acknowledgment with requestId <${acknowledgment.requestId}>")
    }

    private fun processMessageError(messageError: MessageError) {
        log.info(messageError.marker(), "Got MessageError with requestId <${messageError.requestId}>")
        val messageRef = messageError.refToMessageId
        if (messageRef == null) {
            log.warn(messageError.marker(), "Received MessageError without message requestId")
            return
        }
        // TODO store events
        messageError.feil.forEach { error ->
            log.info(messageError.marker(), "Code: ${error.code}, Description: ${error.descriptionText}")
        }
    }
}
