package no.nav.emottak.ebms

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.PayloadRequest
import no.nav.emottak.melding.model.PayloadResponse
import no.nav.emottak.melding.model.ValidationResult
import no.nav.emottak.util.getEnvVar

class CpaRepoClient(clientProvider: () -> HttpClient) {
    private var httpClient = clientProvider.invoke()
    private val cpaRepoEndpoint = getEnvVar("CPA_REPO_URL", "http://cpa-repo")

    suspend fun postValidate(contentId: String, header: Header): ValidationResult {
        log.info("$cpaRepoEndpoint/cpa/validate/$contentId")
        return httpClient.post("$cpaRepoEndpoint/cpa/validate/$contentId") {
            setBody(header)
            contentType(ContentType.Application.Json)
        }.body()
    }
}

class PayloadProcessingClient(clientProvider: () -> HttpClient) {
    private var httpClient = clientProvider.invoke()
    private val payloadProcessorEndpoint = getEnvVar("PAYLOAD_PROCESSOR_URL", "http://ebms-payload/payload")

    suspend fun postPayloadRequest(payloadRequest: PayloadRequest): PayloadResponse {
        return httpClient.post(payloadProcessorEndpoint) {
            setBody(payloadRequest)
            contentType(ContentType.Application.Json)
        }.body()
    }
}
