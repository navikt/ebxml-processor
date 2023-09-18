package no.nav.emottak.cpa

import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.emottak.cpa.config.DatabaseConfig
import no.nav.emottak.cpa.config.mapHikariConfig

fun main() {
    val database = Database(mapHikariConfig(DatabaseConfig()))
    database.migrate()

    embeddedServer(Netty, port = 8080) {

        routing {
            get("/cpa/{id}") {
                val cpaId = call.parameters["id"] ?: throw BadRequestException("Mangler CPA ID")
                val cpa = getCpa(cpaId) ?: throw NotFoundException("Fant ikke CPA")
                call.respond(cpa)
            }

            get("/cpa/{id}/{herId}/certificate/encryption") {
                val cpaId = call.parameters["id"] ?: throw BadRequestException("Mangler CPA ID")
                val herId = call.parameters["herId"] ?: throw BadRequestException("Mangler HER ID")
                val cpa = getCpa(cpaId) ?: throw NotFoundException("Fant ikke CPA")

                call.respond(cpa.getCertificateForEncryption(herId))
            }
        }
    }.start(wait = true)

}
