package no.nav.helsemelding.ediadapter.server

import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.await.awaitAll
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders.Accept
import io.ktor.serialization.kotlinx.json.json
import io.micrometer.prometheus.PrometheusConfig.DEFAULT
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.serialization.json.Json
import no.nav.helsemelding.ediadapter.server.config.Config
import no.nav.helsemelding.ediadapter.server.plugin.DpopAuth
import no.nav.helsemelding.ediadapter.server.util.DpopJwtProvider
import no.nav.helsemelding.ediadapter.server.util.DpopTokenUtil

private val log = KotlinLogging.logger {}

data class Dependencies(
    val httpClient: HttpClient,
    val meterRegistry: PrometheusMeterRegistry
)

internal suspend fun ResourceScope.metricsRegistry(): PrometheusMeterRegistry =
    install({ PrometheusMeterRegistry(DEFAULT) }) { p, _: ExitCase ->
        p.close().also { log.info { "Closed prometheus registry" } }
    }

internal suspend fun ResourceScope.httpClientEngine(): HttpClientEngine =
    install({ CIO.create() }) { e, _: ExitCase -> e.close().also { log.info { "Closed http client engine" } } }

internal suspend fun ResourceScope.httpTokenClientEngine(): HttpClientEngine =
    install({ CIO.create() }) { e, _: ExitCase -> e.close().also { log.info { "Closed http token client engine" } } }

private fun httpTokenClient(config: Config, clientEngine: HttpClientEngine): HttpClient =
    HttpClient(clientEngine) {
        install(HttpTimeout) {
            connectTimeoutMillis = config.httpTokenClient.connectionTimeout.value
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
        connectTimeoutMillis = config.httpClient.connectionTimeout.value
    }
    install(Logging) { level = config.httpClient.logLevel }
    install(ContentNegotiation) { json() }
    install(DpopAuth) {
        dpopJwtProvider = jwtProvider
        loadTokens = { dpopTokenUtil.obtainDpopTokens() }
    }
    defaultRequest {
        url(config.nhn.baseUrl.toString())

        val httpClient = config.httpClient

        header(API_VERSION, httpClient.apiVersionHeader.value)
        header(NHN_SOURCE_SYSTEM, httpClient.sourceSystemHeader.value)
        header(Accept, httpClient.acceptTypeHeader.value)
    }
}

suspend fun ResourceScope.dependencies(): Dependencies = awaitAll {
    val config = config()

    val metricsRegistry = async { metricsRegistry() }
    val httpTokenClientEngine = async { httpTokenClientEngine() }
    val httpTokenClient = async { httpTokenClient(config, httpTokenClientEngine.await()) }.await()
    val httpClientEngine = async { httpClientEngine() }.await()

    val dpopJwtProvider = DpopJwtProvider(config)
    val dpopTokenUtil = DpopTokenUtil(config, dpopJwtProvider, httpTokenClient)

    val httpClient = async { httpClient(config, dpopJwtProvider, dpopTokenUtil, httpClientEngine) }

    Dependencies(
        httpClient.await(),
        metricsRegistry.await()
    )
}
