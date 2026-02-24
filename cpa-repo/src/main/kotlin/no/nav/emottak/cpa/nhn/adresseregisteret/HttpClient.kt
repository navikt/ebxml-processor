package no.nav.emottak.cpa.nhn.adresseregisteret

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders.Accept
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.nav.emottak.cpa.configuration.config

private const val API_VERSION = "api-version"
private const val NHN_SOURCE_SYSTEM = "nhn-source-system"

private fun httpTokenClient(): HttpClient =
    HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 2000
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

private fun httpClient(
    jwtProvider: DpopJwtProvider,
    dpopTokenUtil: DpopTokenUtil
): HttpClient = HttpClient(CIO) {
    install(HttpTimeout) {
        connectTimeoutMillis = 3000
    }
    install(ContentNegotiation) { json() }
    install(DpopAuth) {
        dpopJwtProvider = jwtProvider
        loadTokens = { dpopTokenUtil.obtainDpopTokens() }
    }
    defaultRequest {
        url("https://cppa.test.grunndata.nhn.no/api/v1/communicationparty")
        header(Accept, "application/json")
    }
}

fun nhnArHttpClient(): HttpClient {
    val config = config()

    val dpopJwtProvider = DpopJwtProvider(config)
    val dpopTokenUtil = DpopTokenUtil(config, dpopJwtProvider, httpTokenClient())

    return httpClient(dpopJwtProvider, dpopTokenUtil)
}
