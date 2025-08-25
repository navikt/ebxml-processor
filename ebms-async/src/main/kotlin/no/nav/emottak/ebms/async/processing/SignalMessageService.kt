package no.nav.emottak.ebms.async.processing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.model.Acknowledgment
import no.nav.emottak.message.model.EbMSDocument
import no.nav.emottak.message.model.MessageError
import no.nav.emottak.message.xml.getDocumentBuilder
import no.nav.emottak.util.marker
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import java.io.ByteArrayInputStream

class SignalMessageService(
    val cpaValidationService: CPAValidationService,
    val eventRegistrationService: EventRegistrationService
) {

    suspend fun processSignal(requestId: String, content: ByteArray) {
        try {
            val ebxmlSignalMessage = createEbmsMessage(requestId, content)
            eventRegistrationService.registerEventMessageDetails(ebxmlSignalMessage)
            eventRegistrationService.registerEvent(
                eventType = EventType.MESSAGE_READ_FROM_QUEUE,
                requestId = ebxmlSignalMessage.requestId.parseOrGenerateUuid(),
                messageId = ebxmlSignalMessage.messageId,
                eventData = Json.encodeToString(
                    mapOf(EventDataType.QUEUE_NAME.value to config().kafkaSignalReceiver.topic)
                )
            )
            cpaValidationService.validateIncomingMessage(ebxmlSignalMessage)
            when (ebxmlSignalMessage) {
                is Acknowledgment -> {
                    processAcknowledgment(ebxmlSignalMessage)
                }
                is MessageError -> {
                    processMessageError(ebxmlSignalMessage)
                }
                else -> {
                    log.warn(ebxmlSignalMessage.marker(), "Cannot process message as signal message: $requestId")
                    throw RuntimeException("Cannot process message as signal message: $requestId")
                }
            }
        } catch (e: Exception) {
            log.error("Error processing signal requestId $requestId", e)
            throw e
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

    private suspend fun processMessageError(messageError: MessageError) {
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
