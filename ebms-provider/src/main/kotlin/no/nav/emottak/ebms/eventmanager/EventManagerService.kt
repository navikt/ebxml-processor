package no.nav.emottak.ebms.eventmanager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.ebms.EventManagerClient
import no.nav.emottak.ebms.log
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.utils.common.model.DuplicateCheckRequest

class EventManagerService(val httpClient: EventManagerClient) {

    suspend fun isDuplicateMessage(ebmsPayloadMessage: PayloadMessage): Boolean {
        val duplicateCheckRequest = DuplicateCheckRequest(
            requestId = ebmsPayloadMessage.requestId,
            messageId = ebmsPayloadMessage.messageId,
            conversationId = ebmsPayloadMessage.conversationId,
            cpaId = ebmsPayloadMessage.cpaId
        )

        try {
            val duplicateCheckResponse = withContext(Dispatchers.IO) {
                httpClient.duplicateCheck(duplicateCheckRequest)
            }
            return duplicateCheckResponse.isDuplicate
        } catch (e: Exception) {
            log.error("Error during duplicate check", e)
            return false
        }
    }
}
