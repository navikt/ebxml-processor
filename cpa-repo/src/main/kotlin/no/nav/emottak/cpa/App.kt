package no.nav.emottak.cpa

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.routing.get
import no.nav.emottak.cpa.config.DatabaseConfig
import no.nav.emottak.cpa.config.mapHikariConfig
import no.nav.emottak.melding.model.Header

fun main() {
    val database = Database(mapHikariConfig(DatabaseConfig()))
    database.migrate()

    embeddedServer(Netty, port = 8080, module = Application::myApplicationModule).start(wait = true)

}

fun Application.myApplicationModule() {
    routing {
        get("/cpa/{$CPA_ID}") {
            val cpaId = call.parameters[CPA_ID] ?: throw BadRequestException("Mangler $CPA_ID")
            val cpa = getCpa(cpaId) ?: throw NotFoundException("Fant ikke CPA")
            call.respond(cpa)
        }

        post("cpa/validate") {
            val validateRequest = call.receive(Header::class)
            getCpa(validateRequest.cpaId)!!.validate(validateRequest)
        }

        get("/cpa/{$CPA_ID}/her/{$HER_ID}/encryption/certificate") {
            val cpaId = call.parameters[CPA_ID] ?: throw BadRequestException("Mangler $CPA_ID")
            val herId = call.parameters[HER_ID] ?: throw BadRequestException("Mangler $HER_ID")
            val cpa = getCpa(cpaId) ?: throw NotFoundException("Ingen CPA med ID $cpaId funnet")
            val partyInfo = cpa.getHERPartyInfo(herId)
            call.respond(partyInfo.getCertificateForEncryption())
        }

        get("/cpa/{$CPA_ID}/her/{$HER_ID}/signing/certificate/{$ROLE}/{$SERVICE}/{$ACTION}/") {
            val cpaId = call.parameters[CPA_ID] ?: throw BadRequestException("Mangler $CPA_ID")
            val herId = call.parameters[HER_ID] ?: throw BadRequestException("Mangler $HER_ID")
            val role = call.parameters[ROLE] ?: throw BadRequestException("Mangler $ROLE")
            val service = call.parameters[SERVICE] ?: throw BadRequestException("Mangler $SERVICE")
            val action = call.parameters[ACTION] ?: throw BadRequestException("Mangler $ACTION")
            val cpa = getCpa(cpaId) ?: throw NotFoundException("Ingen CPA med ID $cpaId funnet")
            val partyInfo = cpa.getHERPartyInfo(herId)

            call.respond(partyInfo.getCertificateForSignatureValidation(role, service, action))
        }
    }
}

private const val CPA_ID = "cpaId"
private const val HER_ID = "herId"
private const val ROLE = "role"
private const val SERVICE = "service"
private const val ACTION = "action"
