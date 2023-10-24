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
import no.nav.emottak.util.decodeBase64
import org.apache.xml.security.algorithms.MessageDigestAlgorithm
import org.apache.xml.security.signature.XMLSignature
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
    val debug = true
    if (debug) {
        val cert = decodeBase64("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".toByteArray())
        //val timestamp = messageHeader.messageData.timestamp
        //SecurityUtils.validateCertificate(trustStore, certificate, timestamp)
        return SignatureDetails(
            certificate = cert,
            signatureAlgorithm = XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
            hashFunction = MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256
        )
    }

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