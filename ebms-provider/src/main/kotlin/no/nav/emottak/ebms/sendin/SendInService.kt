package no.nav.emottak.ebms.sendin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.ebms.SendInClient
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.util.toSendInRequest
import no.nav.emottak.utils.common.model.SendInResponse

class SendInService(val httpClient: SendInClient) {

    suspend fun sendIn(payloadMessage: PayloadMessage, partnerId: Long? = null): SendInResponse =
        withContext(Dispatchers.IO) {
            httpClient.postSendIn(payloadMessage.toSendInRequest(partnerId))
        }
}
