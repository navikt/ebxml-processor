package no.nav.emottak.cpa

import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.await.awaitAll
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
import io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.json.Json
import no.nav.emottak.cpa.configuration.EdiNhnConfig
import no.nav.emottak.cpa.configuration.config
import no.nav.emottak.cpa.plugin.DpopAuth
import no.nav.emottak.cpa.util.DpopJwtProvider
import no.nav.emottak.cpa.util.DpopTokenUtil
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpRequest

private val logs = LoggerFactory.getLogger("no.nav.emottak.cpa.App")

data class Dependencies(
    val httpClient: HttpClient,
    val meterRegistry: PrometheusMeterRegistry
)

internal suspend fun ResourceScope.metricsRegistry(): PrometheusMeterRegistry =
    install({ PrometheusMeterRegistry(DEFAULT) }) { p, _: ExitCase ->
        p.close().also {
            logs.info("Closed prometheus registry")
        }
    }

suspend fun ResourceScope.httpClientEngine(): HttpClientEngine =
    install({ CIO.create() }) { e, _: ExitCase -> e.close().also { log.info("Closed http client engine") } }

suspend fun ResourceScope.httpTokenClientEngine(): HttpClientEngine =
    install({ CIO.create() }) { e, _: ExitCase -> e.close().also { log.info("Closed http token client engine") } }

fun httpTokenClient(config: EdiNhnConfig, clientEngine: HttpClientEngine): HttpClient =
    HttpClient(clientEngine) {
        install(HttpTimeout) {
            connectTimeoutMillis = config.httpTokenClient.connectionTimeout.value
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

val API_VERSION = "api-version"
val NHN_SOURCE_SYSTEM = "nhn-source-system"

fun httpClient(
    config: EdiNhnConfig,
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
    val config = config().nhnConfig

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

suspend fun fetchData(scope: ResourceScope) {
    // 1. Kall extension function direkte på scope
    val deps = scope.dependencies()

    // 2. Bruk den inkluderte HttpClient
    val httpClient = deps.httpClient

    val request = HttpRequest.newBuilder()
        .uri(URI.create("https://cpe.test.grunndata.nhn.no"))
        .GET()
        .build()

    try {
        // Send forespørsel (synkront)
        // val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        // println("Status: ${response.statusCode()}")
        // println("Body: ${response.body()}")
        println("Body: ${request.expectContinue()}")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
