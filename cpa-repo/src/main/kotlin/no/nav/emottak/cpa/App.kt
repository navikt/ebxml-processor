package no.nav.emottak.cpa

import com.zaxxer.hikari.HikariConfig
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.emottak.cpa.persistence.CPARepository
import no.nav.emottak.cpa.persistence.CPA_DB_NAME
import no.nav.emottak.cpa.persistence.Database
import no.nav.emottak.cpa.persistence.mapHikariConfig
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import org.slf4j.LoggerFactory

internal val log = LoggerFactory.getLogger("no.nav.emottak.cpa.App")
fun main() {
    embeddedServer(Netty, port = 8080, module = cpaApplicationModule(mapHikariConfig(CPA_DB_NAME, role = "user"), mapHikariConfig(CPA_DB_NAME, role = "admin"))).start(wait = true)
}

fun cpaApplicationModule(cpaDbConfig: HikariConfig, cpaMigrationConfig: HikariConfig, emottakDbConfig: HikariConfig? = null): Application.() -> Unit {
    return {
        val database = Database(cpaDbConfig)
        database.migrate(cpaMigrationConfig)
        val cpaRepository = CPARepository(database)
        install(ContentNegotiation) {
            json()
        }
        routing {
            getCPA(cpaRepository)
            deleteCpa(cpaRepository)
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
