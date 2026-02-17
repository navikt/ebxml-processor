package no.nav.emottak.cpa.AR

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders.Accept
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.nav.emottak.cpa.configuration.Config
import no.nav.emottak.cpa.configuration.config

internal suspend fun httpClientEngine(): HttpClientEngine = CIO.create()

internal suspend fun httpTokenClientEngine(): HttpClientEngine = CIO.create()

private fun httpTokenClient(config: Config, clientEngine: HttpClientEngine): HttpClient =
    HttpClient(clientEngine) {
        install(HttpTimeout) {
            connectTimeoutMillis = 30000
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

private const val API_VERSION = "api-version"
private const val NHN_SOURCE_SYSTEM = "nhn-source-system"

private fun httpClient(
    config: Config,
    jwtProvider: DpopJwtProvider,
    dpopTokenUtil: DpopTokenUtil,
    clientEngine: HttpClientEngine
): HttpClient = HttpClient(clientEngine) {
    install(HttpTimeout) {
        connectTimeoutMillis = 30000
    }
    install(ContentNegotiation) { json() }
    install(DpopAuth) {
        dpopJwtProvider = jwtProvider
        loadTokens = { dpopTokenUtil.obtainDpopTokens() }
    }
    defaultRequest {
        url("https://api.test.nhn.no/v2/ar/CommunicationParty")

        header(API_VERSION, "")
        header(NHN_SOURCE_SYSTEM, "httpClient.sourceSystemHeader.value")
        header(Accept, "application/json")
    }
}

suspend fun httpClient(): HttpClient {
    val config = config()

    val httpTokenClientEngine = httpTokenClientEngine()
    val httpTokenClient = httpTokenClient(config, httpTokenClientEngine)
    val httpClientEngine = httpClientEngine()

    val dpopJwtProvider = DpopJwtProvider(config)
    val dpopTokenUtil = DpopTokenUtil(config, dpopJwtProvider, httpTokenClient)

    return httpClient(config, dpopJwtProvider, dpopTokenUtil, httpClientEngine)
}
