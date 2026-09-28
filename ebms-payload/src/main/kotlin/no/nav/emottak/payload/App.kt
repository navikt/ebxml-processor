package no.nav.emottak.payload

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import no.nav.emottak.payload.configuration.config
import no.nav.emottak.payload.util.EventRegistrationService
import no.nav.emottak.payload.util.EventRegistrationServiceImpl
import no.nav.emottak.util.HttpClientUtil
import no.nav.emottak.util.jsonLenient
import no.nav.emottak.utils.kafka.client.EventPublisherClient
import no.nav.emottak.utils.kafka.service.EventLoggingService
import no.nav.emottak.validering.sertifikat.CRLChecker
import no.nav.emottak.validering.sertifikat.CRLStore
import no.nav.emottak.validering.sertifikat.CRLUpdater
import no.nav.emottak.validering.sertifikat.SertifikatValidator
import no.nav.security.token.support.v3.tokenValidationSupport
import org.slf4j.LoggerFactory

internal val log = LoggerFactory.getLogger("no.nav.emottak.payload")
fun main() {
    val appConfig = config()
    val kafkaPublisherClient = EventPublisherClient(appConfig.kafka)
    val eventLoggingService = EventLoggingService(appConfig.eventLogging, kafkaPublisherClient)
    val eventRegistrationService = EventRegistrationServiceImpl(eventLoggingService)
    val crlStore = CRLStore(defaultCRLLists)
    val crlUpdater = CRLUpdater(
        httpClient = HttpClientUtil.client,
        crlStore = crlStore,
        refreshInterval = appConfig.crl.refreshInterval,
        issuerList = config.caList.filter { it.crlUrl != null }.associate { it.dn to it.crlUrl!! }
    )
    runBlocking {
        crlUpdater.refresh()
    }
    val sertifikatValidator = SertifikatValidator(
        crlChecker = CRLChecker(crlStore)
    )

    val processor = Processor(eventRegistrationService, sertifikatValidator)

    val crlUpdaterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    crlUpdater.startIn(crlUpdaterScope)
    try {
        embeddedServer(
            factory = Netty,
            port = 8080,
            module = payloadApplicationModule(processor, eventRegistrationService)
        ).start(wait = true)
    } finally {
        crlUpdaterScope.cancel()
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
