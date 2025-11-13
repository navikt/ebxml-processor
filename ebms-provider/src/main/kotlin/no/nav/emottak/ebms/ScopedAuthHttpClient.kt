package no.nav.emottak.edi

import com.nimbusds.jwt.SignedJWT
import io.ktor.client.HttpClient
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
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.nav.emottak.utils.environment.getEnvVar
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

fun scopedAuthHttpClientEdi(
    scope: String
): () -> HttpClient {
    return {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
            install(Auth) {
                bearer {
                    refreshTokens {
                        proxiedHttpClient().post(
                            getEnvVar(
                                "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT",
                                "http://localhost:3344/AZURE_AD/token"
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

val LENIENT_JSON_PARSER = Json {
    isLenient = true
}
