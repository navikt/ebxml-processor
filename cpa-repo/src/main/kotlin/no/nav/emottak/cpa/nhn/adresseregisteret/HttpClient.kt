package no.nav.emottak.cpa.nhn.adresseregisteret

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders.Accept
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.nav.emottak.cpa.configuration.config
import no.nav.emottak.utils.environment.getEnvVar
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

private val httpProxyUrl = getEnvVar("HTTP_PROXY", "")

private fun httpTokenClient(): HttpClient =
    HttpClient(CIO) {
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL // Logs all details including headers, body, etc.
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 2000
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        engine {
            if (httpProxyUrl.isNotBlank()) {
                proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(URI(httpProxyUrl).host, URI(httpProxyUrl).port))
            }
        }
    }

private fun httpClient(
    jwtProvider: DpopJwtProvider,
    dpopTokenUtil: DpopTokenUtil
): HttpClient = HttpClient(CIO) {
    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.ALL // Logs all details including headers, body, etc.
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 3000
    }
    install(ContentNegotiation) { json() }
    install(DpopAuth) {
        dpopJwtProvider = jwtProvider
        loadTokens = { dpopTokenUtil.obtainDpopTokens() }
    }
    defaultRequest {
        url("https://cpapi.internett.test.grunndata.nhn.no/api/v1/communicationparty")
        header(Accept, "application/json")
    }
    engine {
        if (httpProxyUrl.isNotBlank()) {
            proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(URI(httpProxyUrl).host, URI(httpProxyUrl).port))
        }
    }
}

fun nhnArHttpClient(): HttpClient {
    val config = config()

    val dpopJwtProvider = DpopJwtProvider(config)
    val dpopTokenUtil = DpopTokenUtil(config, dpopJwtProvider, httpTokenClient())

    return httpClient(dpopJwtProvider, dpopTokenUtil)
}
