package no.nav.emottak.ebms.sendin

import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.SendInClient
import no.nav.emottak.ebms.model.PayloadMessage
import no.nav.emottak.melding.model.EbmsProcessing
import no.nav.emottak.melding.model.SendInRequest
import no.nav.emottak.melding.model.SendInResponse

class SendInService(val httpClient: SendInClient) {

    fun sendIn(payloadMessage: PayloadMessage): SendInResponse {
        val sendInRequest = SendInRequest(
            payloadMessage.messageId,
            payloadMessage.conversationId,
            payloadMessage.payload.contentId,
            payloadMessage.payload.payload,
            payloadMessage.addressing,
            EbmsProcessing()
        )
        return runBlocking { httpClient.postSendIn(sendInRequest) }
    }
}
