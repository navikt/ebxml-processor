package no.nav.emottak.ebms

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.PayloadRequest
import no.nav.emottak.melding.model.PayloadResponse
import no.nav.emottak.melding.model.SignatureDetails
import no.nav.emottak.melding.model.SignatureDetailsRequest
import no.nav.emottak.melding.model.ValidationResult
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader


private val httpClientUtil = HttpClientUtil()
private const val payloadProcessorEndpoint = "http://ebms-payload/payload"
private const val cpaRepoEndpoint = "http://cpa-repo"
private const val validatorEndpoint = "$cpaRepoEndpoint/validate"

fun getPublicSigningDetails(messageHeader: MessageHeader): SignatureDetails = runBlocking {
    getPublicSigningDetails(messageHeader.cpaId, messageHeader.from.partyId[0].type, messageHeader.from.partyId[0].value, messageHeader.service.value, messageHeader.action, messageHeader.from.role)
}

suspend fun getPublicSigningDetails(cpaId: String, partyType: String, partyId: String, service: String, action: String, role: String): SignatureDetails {
    val request = SignatureDetailsRequest(
        cpaId = cpaId,
        partyType = partyType,
        partyId = partyId,
        role = role,
        service = service,
        action = action
    )
    return httpClientUtil.postSignatureDetailsRequest("$cpaRepoEndpoint/signing/certificate", request)
}

class HttpClientUtil {

    private val client = HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun postPayloadRequest(payloadRequest: PayloadRequest): PayloadResponse {
        return client.post(payloadProcessorEndpoint) {
            setBody(payloadRequest)
            contentType(Json)
        }.body()
    }
    suspend fun postValidate(header: Header) : ValidationResult {
        return client.post(validatorEndpoint) {
            this.url {
                this.path("/cpa/validate")
            }
            setBody(header)
        }.body()
    }

    suspend fun postSignatureDetailsRequest(urlString: String, request: SignatureDetailsRequest): SignatureDetails {
        return client.post(urlString) {
            setBody(request)
            contentType(Json)
        }.body()
    }

    suspend fun makeHttpRequest(urlString: String): HttpResponse {
        val response: HttpResponse = client.request(urlString) {
            method = HttpMethod.Get
        }
        return response
    }

}