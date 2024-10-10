package no.nav.emottak.ebms

import com.nimbusds.jwt.SignedJWT
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.message.model.PayloadResponse
import no.nav.emottak.message.model.SendInRequest
import no.nav.emottak.message.model.SendInResponse
import no.nav.emottak.message.model.ValidationRequest
import no.nav.emottak.message.model.ValidationResult
import no.nav.emottak.util.getEnvVar
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

class CpaRepoClient(clientProvider: () -> HttpClient) {
    private var httpClient = clientProvider.invoke()
    private val cpaRepoEndpoint = getEnvVar("CPA_REPO_URL", "http://cpa-repo.team-emottak.svc.nais.local")

    suspend fun postValidate(contentId: String, validationRequest: ValidationRequest): ValidationResult {
        log.debug("CPA endepunkt: $cpaRepoEndpoint/cpa/validate/$contentId")
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
    private var httpClient = clientProvider.invoke()
    private val sendInEndpoint = getEnvVar("SEND_IN_URL", "http://ebms-send-in/fagmelding/synkron")

    suspend fun postSendIn(sendInRequest: SendInRequest): SendInResponse {
        val response = httpClient.post(sendInEndpoint) {
            setBody(sendInRequest)
            contentType(ContentType.Application.Json)
        }
        if (response.status == HttpStatusCode.BadRequest) {
            val errorMessage = response.bodyAsText()
            log.debug("Propagerer feilmelding fra fagsystemet til brukeren: $errorMessage")
            throw Exception(errorMessage)
        }
        return response.body()
    }
}

val EBMS_SEND_IN_SCOPE = getEnvVar(
    "EBMS_SEND_IN_SCOPE",
    "api://" + getEnvVar("NAIS_CLUSTER_NAME", "dev-fss") + ".team-emottak.ebms-send-in/.default"
)
val EBMS_PAYLOAD_SCOPE = getEnvVar(
    "EBMS_PAYLOAD_SCOPE",
    "api://" + getEnvVar("NAIS_CLUSTER_NAME", "dev-fss") + ".team-emottak.ebms-payload/.default"
)

fun defaultHttpClient(): () -> HttpClient {
    return {
        HttpClient(CIO) {
            expectSuccess = true
            install(ContentNegotiation) {
                json()
            }
        }
    }
}

const val AZURE_AD_AUTH = "AZURE_AD"
val LENIENT_JSON_PARSER = Json {
    isLenient = true
}

fun scopedAuthHttpClient(
    scope: String
): () -> HttpClient {
    return {
        HttpClient(CIO) {
            expectSuccess = true
            install(ContentNegotiation) {
                json()
            }
            install(Auth) {
                bearer {
                    refreshTokens {
                        proxiedHttpClient().post(
                            getEnvVar(
                                "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT",
                                "http://localhost:3344/$AZURE_AD_AUTH/token"
                            )
                        ) {
                            headers {
                                header("Content-Type", "application/x-www-form-urlencoded")
                            }
                            setBody(
                                "client_id=" + getEnvVar("AZURE_APP_CLIENT_ID", "dummyclient") +
                                    "&client_secret=" + getEnvVar("AZURE_APP_CLIENT_SECRET", "dummysecret") +
                                    "&scope=" + scope +
                                    "&grant_type=client_credentials"
                            )
                        }.bodyAsText()
                            .let { tokenResponseString ->
                                // log.info("The token response string we received was: $tokenResponseString")
                                SignedJWT.parse(
                                    LENIENT_JSON_PARSER.decodeFromString<Map<String, String>>(tokenResponseString)["access_token"] as String
                                )
                            }
                            .let { parsedJwt ->
                                // log.info("After parsing it, we got: $parsedJwt")
                                BearerTokens(parsedJwt.serialize(), "refresh token is unused")
                            }
                    }
                    sendWithoutRequest {
                        true
                    }
                }
            }
        }
    }
}

private fun proxiedHttpClient() = HttpClient(CIO) {
    engine {
        val httpProxyUrl = getEnvVar("HTTP_PROXY", "")
        if (httpProxyUrl.isNotBlank()) {
            proxy = Proxy(
                Proxy.Type.HTTP,
                InetSocketAddress(URI(httpProxyUrl).toURL().host, URI(httpProxyUrl).toURL().port)
            )
        }
    }
}
