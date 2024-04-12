package no.nav.emottak.cpa

import com.github.dockerjava.zerodep.shaded.org.apache.commons.codec.binary.Base64
import com.zaxxer.hikari.HikariConfig
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
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.emottak.cpa.persistence.CPARepository
import no.nav.emottak.cpa.persistence.Database
import no.nav.emottak.cpa.persistence.cpaDbConfig
import no.nav.emottak.cpa.persistence.cpaMigrationConfig
import no.nav.emottak.cpa.persistence.gammel.PartnerRepository
import no.nav.emottak.cpa.persistence.oracleConfig
import no.nav.emottak.util.getEnvVar
import no.nav.security.token.support.v2.tokenValidationSupport
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import org.slf4j.LoggerFactory

internal val log = LoggerFactory.getLogger("no.nav.emottak.cpa.App")
fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        module = cpaApplicationModule(
            cpaDbConfig.value,
            cpaMigrationConfig.value,
            oracleConfig.value
        )
    ).start(wait = true)
}

const val AZURE_AD_AUTH = "AZURE_AD"

fun cpaApplicationModule(cpaDbConfig: HikariConfig, cpaMigrationConfig: HikariConfig, emottakDbConfig: HikariConfig? = null): Application.() -> Unit {
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

        if (canInitAuthenticatedRoutes()) { // TODO gjerne få til dette med 1 usage av canInit
            install(Authentication) {
                tokenValidationSupport(AZURE_AD_AUTH, Security().config)
            }
        }

        routing {
            if (oracleDb != null) {
                partnerId(PartnerRepository(oracleDb), cpaRepository)
            }
            validateCpa(cpaRepository)
            getCPA(cpaRepository)
            getTimeStamps(cpaRepository)
            getTimeStampsLatest(cpaRepository)
            getCertificate(cpaRepository)
            signingCertificate(cpaRepository)
            registerHealthEndpoints(appMicrometerRegistry)
            if (canInitAuthenticatedRoutes()) { // TODO gjerne få til dette med 1 usage av canInit
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
    // muligens gjenbrukbar løsning?
    val TENANT_ID = getEnvVar("AZURE_APP_TENANT_ID", AZURE_AD_AUTH)
    val AZURE_WELL_KNOWN_URL = getEnvVar(
        "AZURE_APP_WELL_KNOWN_URL",
        "http://localhost:3344/$TENANT_ID/.well-known/openid-configuration"
    )
    if (AZURE_WELL_KNOWN_URL.contains("localhost")) {
        return runBlocking {
            runCatching {
                HttpClient(CIO) {
                }.get(AZURE_WELL_KNOWN_URL).bodyAsText()
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
