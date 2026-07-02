package no.nav.emottak.payload

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.emottak.payload.configuration.config
import no.nav.emottak.payload.util.EventRegistrationService
import no.nav.emottak.payload.util.EventRegistrationServiceImpl
import no.nav.emottak.util.jsonLenient
import no.nav.emottak.utils.environment.getEnvVar
import no.nav.emottak.utils.kafka.client.EventPublisherClient
import no.nav.emottak.utils.kafka.service.EventLoggingService
import no.nav.emottak.validering.sertifikat.CRLChecker
import no.nav.emottak.validering.sertifikat.CRLRetriever
import no.nav.emottak.validering.sertifikat.SertifikatValidator
import no.nav.emottak.validering.sertifikat.defaultCRLLists
import no.nav.security.token.support.v3.tokenValidationSupport
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

internal val log = LoggerFactory.getLogger("no.nav.emottak.payload")
fun main() {
    val kafkaPublisherClient = EventPublisherClient(config().kafka)
    val eventLoggingService = EventLoggingService(config().eventLogging, kafkaPublisherClient)
    val eventRegistrationService = EventRegistrationServiceImpl(eventLoggingService)
    val sertifikatValidator = SertifikatValidator(
        crlChecker = CRLChecker(
            crlRetriever = CRLRetriever(
                httpClient = defaultHttpClient().invoke(),
                issuerList = defaultCRLLists
            )
        )
    )

    val processor = Processor(eventRegistrationService, sertifikatValidator)

    embeddedServer(
        factory = Netty,
        port = 8080,
        module = payloadApplicationModule(processor, eventRegistrationService)
    ).start(wait = true)
}
private val httpProxyUrl = getEnvVar("HTTP_PROXY", "")
fun defaultHttpClient(): () -> HttpClient = {
    HttpClient(CIO) {
        expectSuccess = true
        engine {
            if (httpProxyUrl.isNotBlank()) {
                proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(URL(httpProxyUrl).host, URL(httpProxyUrl).port))
            }
        }
    }
}

fun payloadApplicationModule(
    processor: Processor,
    eventRegistrationService: EventRegistrationService
): Application.() -> Unit {
    return {
        install(ContentNegotiation) {
            jsonLenient()
        }
        val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        install(MicrometerMetrics) {
            registry = appMicrometerRegistry
        }
        install(Authentication) {
            tokenValidationSupport(AZURE_AD_AUTH, AuthConfig.getTokenSupportConfig())
        }

        routing {
            registerHealthEndpoints(appMicrometerRegistry)

            authenticate(AZURE_AD_AUTH) {
                postPayload(processor, eventRegistrationService)
            }
        }
    }
}
