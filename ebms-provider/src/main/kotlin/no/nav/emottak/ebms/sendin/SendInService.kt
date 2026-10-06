package no.nav.emottak.ebms.sendin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.ebms.SendInClient
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.util.toSendInRequest
import no.nav.emottak.utils.common.model.SendInResponse

class SendInService(val httpClient: SendInClient) {

    suspend fun sendInSynkron(payloadMessage: PayloadMessage, partnerId: Long? = null): SendInResponse =
        withContext(Dispatchers.IO) {
            httpClient.postSendInSynkron(payloadMessage.toSendInRequest(partnerId))
        }

    suspend fun sendInAsynkron(payloadMessage: PayloadMessage, partnerId: Long? = null) =
        withContext(Dispatchers.IO) {
            httpClient.postSendInAsynkron(payloadMessage.toSendInRequest(partnerId))
        }
}
