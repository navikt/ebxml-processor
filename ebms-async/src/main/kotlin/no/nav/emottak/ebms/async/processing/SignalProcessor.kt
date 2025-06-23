package no.nav.emottak.ebms.async.processing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.message.model.Acknowledgment
import no.nav.emottak.message.model.EbMSDocument
import no.nav.emottak.message.model.EbmsFail
import no.nav.emottak.message.xml.getDocumentBuilder
import java.io.ByteArrayInputStream
import kotlin.uuid.ExperimentalUuidApi

class SignalProcessor(
    val validator: DokumentValidator
) {

    @OptIn(ExperimentalUuidApi::class)
    suspend fun processSignal(requestId: String, content: ByteArray) {
        try {
            val ebxmlSignalMessage = createEbmsMessage(requestId, content)
            validator.validateIn(ebxmlSignalMessage)
            when (ebxmlSignalMessage) {
                is Acknowledgment -> {
                    processAcknowledgment(ebxmlSignalMessage)
                }
                is EbmsFail -> {
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

    private fun processMessageError(ebmsFail: EbmsFail) {
        log.info(ebmsFail.marker(), "Got MessageError with requestId <${ebmsFail.requestId}>")
        val messageRef = ebmsFail.refToMessageId
        if (messageRef == null) {
            log.warn(ebmsFail.marker(), "Received MessageError without message requestId")
            return
        }
        // TODO store events
        ebmsFail.feil.forEach { error ->
            log.info(ebmsFail.marker(), "Code: ${error.code}, Description: ${error.descriptionText}")
        }
    }
}
