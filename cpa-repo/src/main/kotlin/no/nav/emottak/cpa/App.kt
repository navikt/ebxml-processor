package no.nav.emottak.cpa

import com.github.dockerjava.zerodep.shaded.org.apache.commons.codec.binary.Base64
import com.zaxxer.hikari.HikariConfig
import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorRuntime
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
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
import kotlinx.coroutines.runBlocking
import no.nav.emottak.cpa.auth.AZURE_AD_AUTH
import no.nav.emottak.cpa.auth.AuthConfig
import no.nav.emottak.cpa.configuration.config
import no.nav.emottak.cpa.persistence.CPARepository
import no.nav.emottak.cpa.persistence.Database
import no.nav.emottak.cpa.persistence.cpaDbConfig
import no.nav.emottak.cpa.persistence.cpaMigrationConfig
import no.nav.emottak.cpa.persistence.gammel.PartnerRepository
import no.nav.emottak.cpa.persistence.oracleConfig
import no.nav.emottak.cpa.util.EventRegistrationService
import no.nav.emottak.cpa.util.EventRegistrationServiceImpl
import no.nav.emottak.utils.environment.getEnvVar
import no.nav.emottak.utils.kafka.client.EventPublisherClient
import no.nav.emottak.utils.kafka.service.EventLoggingService
import no.nav.security.token.support.v3.tokenValidationSupport
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import org.slf4j.LoggerFactory

internal val log = LoggerFactory.getLogger("no.nav.emottak.cpa.App")
fun main() {
    if (getEnvVar("NAIS_CLUSTER_NAME", "local") != "prod-fss") {
        DecoroutinatorRuntime.load()
    }

    val kafkaPublisherClient = EventPublisherClient(config().kafka)
    val eventLoggingService = EventLoggingService(config().eventLogging, kafkaPublisherClient)
    val eventRegistrationService = EventRegistrationServiceImpl(eventLoggingService)

    embeddedServer(
        Netty,
        port = 8080,
        module = cpaApplicationModule(
            cpaDbConfig.value,
            cpaMigrationConfig.value,
            oracleConfig.value,
            eventRegistrationService
        )
    ).start(wait = true)
}

fun cpaApplicationModule(
    cpaDbConfig: HikariConfig,
    cpaMigrationConfig: HikariConfig,
    emottakDbConfig: HikariConfig? = null,
    eventRegistrationService: EventRegistrationService
): Application.() -> Unit {
    return {
        val database = Database(cpaDbConfig)
        database.migrate(cpaMigrationConfig)
        val cpaRepository = CPARepository(database)
        val oracleDb = if (emottakDbConfig != null) Database(emottakDbConfig) else null

        install(ContentNegotiation) {
            json()
        }
        val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        install(MicrometerMetrics) {
            registry = appMicrometerRegistry
        }

        if (canInitAuthenticatedRoutes()) {
            install(Authentication) {
                tokenValidationSupport(AZURE_AD_AUTH, AuthConfig.getTokenSupportConfig())
            }
        }

        routing {
            if (oracleDb != null) {
                partnerId(PartnerRepository(oracleDb), cpaRepository)
                validateCpa(cpaRepository, PartnerRepository(oracleDb), eventRegistrationService)
            }
            getCPA(cpaRepository)
            getTimeStamps(cpaRepository)
            getTimeStampsLatest(cpaRepository)
            getTimeStampsLastUsed(cpaRepository)
            getCertificate(cpaRepository)
            signingCertificate(cpaRepository)
            getMessagingCharacteristics(cpaRepository)
            registerHealthEndpoints(appMicrometerRegistry, cpaRepository)

            if (canInitAuthenticatedRoutes().also { log.info("INIT AZURE ENDPOINTS: [$it]") }) {
                authenticate(AZURE_AD_AUTH) {
                    whoAmI()
                    deleteCpa(cpaRepository)
                    deleteAllCPA(cpaRepository)
                    postCpa(cpaRepository)
                }
            }
        }
    }
}

fun canInitAuthenticatedRoutes(): Boolean {
    val azureWellKnownUrl = AuthConfig.getAzureWellKnownUrl()
    if (azureWellKnownUrl.contains("localhost")) {
        return runBlocking {
            runCatching {
                HttpClient(CIO) {
                }.get(azureWellKnownUrl).bodyAsText()
            }.onFailure {
                log.warn("Skipping authenticated endpoint initialization. (No connection to Oauth2Server)")
                return@runBlocking false
            }
            return@runBlocking true
        }
    }
    return true
}

fun CollaborationProtocolAgreement.asText(): String {
    return xmlMarshaller.marshal(this)
}

fun String.decodeBase64Mime(): String {
    return if (!this.isNullOrEmpty()) String(Base64.decodeBase64(this)) else this
}
