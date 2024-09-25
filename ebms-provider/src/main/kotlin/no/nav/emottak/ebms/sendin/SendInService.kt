package no.nav.emottak.ebms.sendin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.ebms.SendInClient
import no.nav.emottak.ebms.model.PayloadMessage
import no.nav.emottak.melding.model.EbmsProcessing
import no.nav.emottak.melding.model.SendInRequest
import no.nav.emottak.melding.model.SendInResponse

class SendInService(val httpClient: SendInClient) {

    suspend fun sendIn(payloadMessage: PayloadMessage): SendInResponse {
        val sendInRequest = SendInRequest(
            payloadMessage.messageId,
            payloadMessage.conversationId,
            payloadMessage.payload.contentId,
            payloadMessage.payload.bytes,
            payloadMessage.addressing,
            payloadMessage.cpaId,
            EbmsProcessing(),
            payloadMessage.payload.signedOf
        )
        return withContext(Dispatchers.IO) {
            httpClient.postSendIn(sendInRequest)
        }
    }
}
