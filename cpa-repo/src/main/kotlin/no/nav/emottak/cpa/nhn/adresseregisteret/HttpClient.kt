package no.nav.emottak.cpa.nhn.adresseregisteret

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.emottak.cpa.configuration.Nhn
import no.nav.emottak.cpa.configuration.NhnOAuthConfig
import no.nav.emottak.cpa.configuration.config
import no.nav.emottak.cpa.configuration.resolve
import no.nav.emottak.utils.environment.getEnvVar
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

private val httpProxyUrl = getEnvVar("HTTP_PROXY", "")

private fun basicHttpClient(): HttpClient =
    HttpClient(CIO) {
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

private fun dpopHttpClient(
    jwtProvider: DpopJwtProvider,
    dpopTokenUtil: DpopTokenUtil
): HttpClient = HttpClient(CIO) {
    install(HttpTimeout) {
        connectTimeoutMillis = 3000
    }
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(DpopAuth) {
        dpopJwtProvider = jwtProvider
        loadTokens = { dpopTokenUtil.obtainDpopTokens() }
    }
    engine {
        if (httpProxyUrl.isNotBlank()) {
            proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(URI(httpProxyUrl).host, URI(httpProxyUrl).port))
        }
    }
}

fun nhnArHttpClient(
    nhnOAuthConfig: NhnOAuthConfig = config.nhnOAuth,
    nhnConfig: Nhn = config.nhn
): HttpClient {
    val basicHttpClient = basicHttpClient()
    val nhnOAuth = runBlocking { nhnOAuthConfig.resolve(basicHttpClient) }
    val dpopJwtProvider = DpopJwtProvider(nhnOAuth, nhnConfig)
    val dpopTokenUtil = DpopTokenUtil(nhnOAuth, dpopJwtProvider, basicHttpClient)

    return dpopHttpClient(dpopJwtProvider, dpopTokenUtil)
}
