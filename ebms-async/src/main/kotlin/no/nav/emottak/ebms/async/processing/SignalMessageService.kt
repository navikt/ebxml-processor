package no.nav.emottak.ebms.async.processing

import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.persistence.repository.MessagePendingAckRepository
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.exception.EbmsException
import no.nav.emottak.message.model.Acknowledgment
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.MessageError
import no.nav.emottak.util.marker
import no.nav.emottak.util.retrievePublicX509Certificate
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import kotlin.coroutines.cancellation.CancellationException

class SignalMessageService(
    val cpaValidationService: CPAValidationService,
    val eventRegistrationService: EventRegistrationService,
    val messagePendingAckRepository: MessagePendingAckRepository
) {

    suspend fun processSignal(requestId: String, ebxmlSignalMessage: EbmsMessage) {
        try {
            when (ebxmlSignalMessage) {
                is Acknowledgment -> processAcknowledgment(ebxmlSignalMessage)
                is MessageError -> processMessageError(ebxmlSignalMessage)
                else -> {
                    log.warn(ebxmlSignalMessage.marker(), "Cannot process message as signal message: $requestId")
                    throw RuntimeException("Cannot process message as signal message: $requestId")
                }
            }
        } catch (e: EbmsException) {
            log.error("EbmsException processing signal requestId [$requestId]. Message: ${e.message}", e)
        } catch (e: Exception) {
            log.error("Unknown error processing signal requestId [$requestId]. Message: ${e.message}", e)
            throw e
        }
    }

    suspend fun processAcknowledgment(acknowledgment: Acknowledgment) {
        val checkSignature = messagePendingAckRepository.wasAckSignatureRequested(acknowledgment.refToMessageId)
        if (checkSignature == null) {
            log.info(acknowledgment.marker(), "No pending message found for messageId <${acknowledgment.refToMessageId}>")
            return
        }
        eventRegistrationService.registerEventMessageDetails(acknowledgment)
        validateIncomingSignal(
            message = acknowledgment,
            checkSignature = checkSignature
        )
        eventRegistrationService.registerEvent(
            eventType = EventType.MESSAGEFLOW_COMPLETED,
            conversationId = acknowledgment.conversationId,
            messageId = acknowledgment.refToMessageId,
            requestId = acknowledgment.requestId.parseOrGenerateUuid()
        )
        log.info(acknowledgment.marker(), "Got acknowledgment with requestId <${acknowledgment.requestId}>")
        messagePendingAckRepository.registerAckForMessage(acknowledgment.refToMessageId)
    }

    suspend fun processMessageError(messageError: MessageError) {
        if (!messagePendingAckRepository.existsForMessageId(messageError.refToMessageId)) {
            log.info(messageError.marker(), "No pending message found for messageId <${messageError.refToMessageId}>")
            return
        }
        eventRegistrationService.registerEventMessageDetails(messageError)
        validateIncomingSignal(
            message = messageError,
            checkSignature = true
        )
        log.info(messageError.marker(), "Got MessageError with requestId <${messageError.requestId}>")
        messageError.feil.forEach { error ->
            log.warn(messageError.marker(), "Code: ${error.code}, Description: ${error.descriptionText}")
            eventRegistrationService.registerEvent(
                eventType = EventType.UNKNOWN_ERROR_OCCURRED,
                requestId = messageError.requestId.parseOrGenerateUuid(),
                messageId = messageError.refToMessageId,
                eventData = Json.encodeToString(
                    mapOf(
                        EventDataType.ERROR_MESSAGE to "${error.code}: ${error.descriptionText}"
                    )
                ),
                conversationId = messageError.conversationId
            )
        }
    }

    /**
     * Validates the signal against the CPA and, when requested, its signature.
     * Failures are only logged and registered as events; signal processing always continues.
     */
    private suspend fun validateIncomingSignal(
        message: EbmsMessage,
        checkSignature: Boolean
    ) {
        val validationResult = try {
            cpaValidationService.getValidationResult(Direction.IN, message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(message.marker(), "Could not validate signal against CPA, continuing processing: ${e.message}", e)
            eventRegistrationService.registerCpaValidationFailed(message, e.message)
            return
        }

        if (!validationResult.valid()) {
            val errorMessage = validationResult.error.orEmpty().joinToString(", ") { "${it.code}: ${it.descriptionText}" }
            log.warn(message.marker(), "CPA validation failed for signal, continuing processing: $errorMessage")
            eventRegistrationService.registerCpaValidationFailed(message, errorMessage)
            if (checkSignature) {
                log.warn(message.marker(), "Skipping signature validation of signal because CPA validation failed")
            }
            return
        }

        if (checkSignature) {
            try {
                cpaValidationService.validateResult(
                    validationResult = validationResult,
                    message = message,
                    checkSignature = true
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: EbmsException) {
                log.warn(message.marker(), "Signature validation failed for signal, continuing processing: ${e.message}", e)
                val signingCertificate = runCatching {
                    validationResult.payloadProcessing?.signingCertificate?.retrievePublicX509Certificate()
                }.onFailure {
                    log.warn(message.marker(), "Could not read signing certificate from CPA: ${it.message}", it)
                }.getOrNull()
                eventRegistrationService.registerSignatureValidationFailed(message, e.message, signingCertificate)
            } catch (e: Exception) {
                log.error(message.marker(), "Unexpected error during signature validation of signal, continuing processing: ${e.message}", e)
            }
        }
    }
}
