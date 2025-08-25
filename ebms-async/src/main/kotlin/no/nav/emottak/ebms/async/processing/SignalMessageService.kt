package no.nav.emottak.ebms.async.processing

import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.model.Acknowledgment
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.MessageError
import no.nav.emottak.util.marker

class SignalMessageService(
    val cpaValidationService: CPAValidationService
) {

    suspend fun processSignal(requestId: String, signalMessage: EbmsMessage) {
        try {
            when (signalMessage) {
                is Acknowledgment -> processAcknowledgment(signalMessage)
                is MessageError -> processMessageError(signalMessage)
                else -> {
                    throw RuntimeException("Cannot process message as signal message: $requestId")
                }
            }
        } catch (e: Exception) {
            log.error("Error processing signal requestId $requestId", e)
            throw e
        }
    }

    private suspend fun processAcknowledgment(acknowledgment: Acknowledgment) {
        cpaValidationService.validateIncomingMessage(acknowledgment)
        log.info(acknowledgment.marker(), "Got acknowledgment with requestId <${acknowledgment.requestId}>")
    }

    private suspend fun processMessageError(messageError: MessageError) {
        cpaValidationService.validateIncomingMessage(messageError)
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
