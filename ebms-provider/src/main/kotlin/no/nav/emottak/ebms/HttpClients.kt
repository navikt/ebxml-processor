package no.nav.emottak.ebms

import com.nimbusds.jwt.SignedJWT
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.headers
import kotlinx.serialization.json.Json
import no.nav.emottak.auth.AZURE_AD_AUTH
import no.nav.emottak.auth.AuthConfig
import no.nav.emottak.melding.model.PayloadRequest
import no.nav.emottak.melding.model.PayloadResponse
import no.nav.emottak.melding.model.SendInRequest
import no.nav.emottak.melding.model.SendInResponse
import no.nav.emottak.melding.model.ValidationRequest
import no.nav.emottak.melding.model.ValidationResult
import no.nav.emottak.util.getEnvVar

class CpaRepoClient(clientProvider: () -> HttpClient) {
    private var httpClient = clientProvider.invoke()
    private val cpaRepoEndpoint = getEnvVar("CPA_REPO_URL", "http://cpa-repo")

    suspend fun postValidate(contentId: String, validationRequest: ValidationRequest): ValidationResult {
        log.info("$cpaRepoEndpoint/cpa/validate/$contentId")
        return httpClient.post("$cpaRepoEndpoint/cpa/validate/$contentId") {
            setBody(validationRequest)
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

class SendInClient(clientProvider: () -> HttpClient) {
    private val httpClient = clientProvider.invoke()
    private val sendInEndpoint = getEnvVar("SEND_IN_URL", "http://ebms-send-in/fagmelding/synkron")

    suspend fun postSendIn(sendInRequest: SendInRequest): SendInResponse {
        return httpClient.post(sendInEndpoint) {
            setBody(sendInRequest)
            contentType(ContentType.Application.Json)
        }.body()
    }
}

val LENIENT_JSON_PARSER = Json {
    isLenient = true
}

suspend fun getEbmsSendInToken(): BearerTokens {
    val requestBody =
        "client_id=" + getEnvVar("AZURE_APP_CLIENT_ID", "ebms-send-in") +
            "&client_secret=" + getEnvVar(" ", "dummysecret") +
            "&scope=" + AuthConfig.getEbmsSendInScope() +
            "&grant_type=client_credentials"

    return HttpClient(CIO).post(
        getEnvVar(
            "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT",
            "http://localhost:3344/$AZURE_AD_AUTH/token"
        )
    ) {
        headers {
            header("Content-Type", "application/x-www-form-urlencoded")
        }
        setBody(requestBody)
    }.bodyAsText()
        .let { tokenResponseString ->
            SignedJWT.parse(
                LENIENT_JSON_PARSER.decodeFromString<Map<String, String>>(tokenResponseString)["access_token"] as String
            )
        }
        .let { parsedJwt ->
            BearerTokens(parsedJwt.serialize(), "ignoredRefreshToken")
        }
}
