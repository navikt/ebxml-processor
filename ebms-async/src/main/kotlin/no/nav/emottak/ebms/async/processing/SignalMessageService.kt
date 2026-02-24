package no.nav.emottak.ebms.async.processing

import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.persistence.repository.MessagePendingAckRepository
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.exception.EbmsException
import no.nav.emottak.message.model.Acknowledgment
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.MessageError
import no.nav.emottak.util.marker
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType

class SignalMessageService(
    val cpaValidationService: CPAValidationService,
    val eventRegistrationService: EventRegistrationService,
    val messagePendingAckRepository: MessagePendingAckRepository
) {

    suspend fun processSignal(requestId: String, ebxmlSignalMessage: EbmsMessage) {
        try {
            eventRegistrationService.registerEventMessageDetails(ebxmlSignalMessage)
            when (ebxmlSignalMessage) {
                is Acknowledgment -> processAcknowledgment(ebxmlSignalMessage)
                is MessageError -> processMessageError(ebxmlSignalMessage)
                else -> {
                    log.warn(ebxmlSignalMessage.marker(), "Cannot process message as signal message: $requestId")
                    throw RuntimeException("Cannot process message as signal message: $requestId")
                }
            }
        } catch (e: EbmsException) {
            log.error("EbmsException processing signal requestId $requestId", e)
        } catch (e: Exception) {
            log.error("Unknown error processing signal requestId $requestId", e)
            throw e
        }
    }

    suspend fun processAcknowledgment(acknowledgment: Acknowledgment) {
        cpaValidationService.validateIncomingMessage(acknowledgment)
        log.info(acknowledgment.marker(), "Got acknowledgment with requestId <${acknowledgment.requestId}>")
        messagePendingAckRepository.registerAckForMessage(acknowledgment.refToMessageId)
    }

    suspend fun processMessageError(messageError: MessageError) {
        cpaValidationService.validateIncomingMessage(messageError)
        log.info(messageError.marker(), "Got MessageError with requestId <${messageError.requestId}>")
        messageError.feil.forEach { error ->
            log.warn(messageError.marker(), "Code: ${error.code}, Description: ${error.descriptionText}")
            eventRegistrationService.registerEvent(
                eventType = EventType.UNKNOWN_ERROR_OCCURRED,
                requestId = messageError.requestId.parseOrGenerateUuid(),
                messageId = messageError.messageId,
                eventData = Json.encodeToString(
                    mapOf(
                        EventDataType.ERROR_MESSAGE to "${error.code}: ${error.descriptionText}"
                    )
                )
            )
        }
    }
}
