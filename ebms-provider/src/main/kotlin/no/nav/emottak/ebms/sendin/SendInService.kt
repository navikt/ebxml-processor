package no.nav.emottak.ebms.sendin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.ebms.SendInClient
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.utils.common.model.EbmsProcessing
import no.nav.emottak.utils.common.model.SendInRequest
import no.nav.emottak.utils.common.model.SendInResponse

class SendInService(val httpClient: SendInClient) {

    suspend fun sendInSynkron(payloadMessage: PayloadMessage, partnerId: Long? = null): SendInResponse = withContext(Dispatchers.IO) {
        httpClient.postSendInSynkron(sendInRequest = convertToSendInRequest(payloadMessage, partnerId))
    }

    suspend fun sendInAsynkron(payloadMessage: PayloadMessage, partnerId: Long? = null) = withContext(Dispatchers.IO) {
        httpClient.postSendInAsynkron(convertToSendInRequest(payloadMessage, partnerId))
    }

    private fun convertToSendInRequest(payloadMessage: PayloadMessage, partnerId: Long? = null): SendInRequest = SendInRequest(
        payloadMessage.messageId,
        payloadMessage.conversationId,
        payloadMessage.payload.contentId,
        payloadMessage.payload.bytes,
        payloadMessage.addressing,
        payloadMessage.cpaId,
        EbmsProcessing(),
        payloadMessage.payload.signedBy,
        payloadMessage.requestId,
        partnerId
    )
}
