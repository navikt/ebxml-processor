package no.nav.emottak.ebms.eventmanager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.ebms.EventManagerClient
import no.nav.emottak.ebms.log
import no.nav.emottak.message.model.DuplicateCheckRequest
import no.nav.emottak.message.model.PayloadMessage

class EventManagerService(val httpClient: EventManagerClient) {

    suspend fun isDuplicateMessage(ebmsPayloadMessage: PayloadMessage): Boolean {
        val duplicateCheckRequest = DuplicateCheckRequest(
            requestId = ebmsPayloadMessage.requestId,
            messageId = ebmsPayloadMessage.messageId,
            conversationId = ebmsPayloadMessage.conversationId,
            cpaId = ebmsPayloadMessage.cpaId
        )
        log.debug("Sending duplicate check request: $duplicateCheckRequest")

        try {
            val duplicateCheckResponse = withContext(Dispatchers.IO) {
                httpClient.duplicateCheck(duplicateCheckRequest)
            }
            log.debug("Duplicate check response received: $duplicateCheckResponse")

            return duplicateCheckResponse.isDuplicate
        }
        catch (e: Exception) {
            log.error("Error during duplicate check", e)
            return false
        }
    }
}
