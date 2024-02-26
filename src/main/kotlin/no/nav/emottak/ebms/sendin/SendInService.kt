package no.nav.emottak.ebms.sendin

import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.PayloadMessage
import no.nav.emottak.ebms.ProcessedMessage
import no.nav.emottak.ebms.SendInClient
import no.nav.emottak.ebms.model.EbmsPayloadMessage
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.EbmsProcessing
import no.nav.emottak.melding.model.SendInRequest
import no.nav.emottak.melding.model.SendInResponse

class SendInService(val httpClient: SendInClient) {



    fun sendIn(ebmsPayloadMessage: PayloadMessage,ebmsProcessing: EbmsProcessing, processedPayload: ByteArray): SendInResponse {
        val sendInRequest = SendInRequest(
            ebmsPayloadMessage.messageId,
            ebmsPayloadMessage.conversationId,
            ebmsPayloadMessage.payload.contentId,
            processedPayload,
            ebmsPayloadMessage.addressing,
            ebmsProcessing
        )
        return runBlocking { httpClient.postSendIn(sendInRequest) }
    }
}
