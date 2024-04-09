package no.nav.emottak.ebms

import com.nimbusds.jwt.SignedJWT
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import no.nav.emottak.auth.AZURE_AD_AUTH
import no.nav.emottak.auth.AuthConfig
import no.nav.emottak.melding.model.*
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

class SendInClient() {
    private val baseUrl = getEnvVar("URL_EBMS_SEND_IN_BASE", "http://ebms-send-in.team-emottak.svc.nais.local")

    var httpClient = HttpClient(CIO) {
        expectSuccess = true
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json()
        }
        install(Auth) {
            bearer {
                refreshTokens {
                    getEbmsSendInToken()
                }
                sendWithoutRequest { request ->
                    request.url.host == baseUrl
                }
            }
        }
    }

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
        "client_id=" + getEnvVar("AZURE_APP_CLIENT_ID", "ebms-provider") +
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
