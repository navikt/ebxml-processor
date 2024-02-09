package no.nav.emottak.cpa

import com.zaxxer.hikari.HikariConfig
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import no.nav.emottak.cpa.persistence.CPARepository
import no.nav.emottak.cpa.persistence.Database
import no.nav.emottak.cpa.persistence.cpaDbConfig
import no.nav.emottak.cpa.persistence.cpaMigrationConfig
import no.nav.emottak.cpa.persistence.gammel.PartnerRepository
import no.nav.emottak.cpa.persistence.oracleConfig
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

fun cpaApplicationModule(cpaDbConfig: HikariConfig, cpaMigrationConfig: HikariConfig, emottakDbConfig: HikariConfig? = null): Application.() -> Unit {
    return {
        println("test")
        val database = Database(cpaDbConfig)
        database.migrate(cpaMigrationConfig)
        val cpaRepository = CPARepository(database)
        val oracleDb = if (emottakDbConfig != null) Database(emottakDbConfig) else null

        install(ContentNegotiation) {
            json()
        }
        routing {
            if (oracleDb != null) {
                partnerId(PartnerRepository(oracleDb), cpaRepository)
            }
            getCPA(cpaRepository)
            deleteCpa(cpaRepository)
            deleteAllCPA(cpaRepository)
            getTimeStamps(cpaRepository)
            getTimeStampsLatest(cpaRepository)
            postCpa(cpaRepository)
            validateCpa(cpaRepository)
            getCertificate(cpaRepository)
            signingCertificate(cpaRepository)
        }
    }
}

fun CollaborationProtocolAgreement.asText(): String {
    return xmlMarshaller.marshal(this)
}
