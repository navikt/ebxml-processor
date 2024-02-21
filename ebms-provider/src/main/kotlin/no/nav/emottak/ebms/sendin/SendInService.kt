package no.nav.emottak.ebms.sendin

import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.SendInClient
import no.nav.emottak.ebms.model.EbmsPayloadMessage
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.EbmsProcessing
import no.nav.emottak.melding.model.SendInRequest
import no.nav.emottak.melding.model.SendInResponse

class SendInService(val httpClient: SendInClient) {

    fun sendIn(ebmsPayloadMessage: EbmsPayloadMessage, addressing: Addressing, ebmsProcessing: EbmsProcessing, processedPayload: ByteArray): SendInResponse {
        val sendInRequest = SendInRequest(
            ebmsPayloadMessage.messageHeader.messageData.messageId,
            ebmsPayloadMessage.messageHeader.conversationId,
            ebmsPayloadMessage.attachments.first().contentId,
            processedPayload,
            addressing,
            ebmsProcessing
        )
        return runBlocking { httpClient.postSendIn(sendInRequest) }
    }
}
