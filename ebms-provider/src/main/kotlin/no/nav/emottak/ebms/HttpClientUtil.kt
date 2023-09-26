package no.nav.emottak.ebms

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.runBlocking
import no.nav.emottak.melding.model.PayloadRequest
import no.nav.emottak.melding.model.PayloadResponse


private val httpClientUtil = HttpClientUtil()
private const val payloadProcessorEndpoint = "http://payload-processor/payload"

fun postPayloadRequest(payloadRequest: PayloadRequest): PayloadResponse = runBlocking {
    httpClientUtil.postPayloadRequest(payloadRequest)
}

class HttpClientUtil {

    private val client = HttpClient(CIO) {
        expectSuccess = true
    }

    suspend fun postPayloadRequest(payloadRequest: PayloadRequest): PayloadResponse {
        return client.post(payloadProcessorEndpoint) {
            setBody(payloadRequest)
        }.body()
    }

}