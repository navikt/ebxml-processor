package no.nav.emottak.cpa

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.routing.get
import no.nav.emottak.cpa.config.DatabaseConfig
import no.nav.emottak.cpa.config.mapHikariConfig
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.SignatureDetailsRequest
import no.nav.emottak.melding.model.ValidationResult
import org.slf4j.LoggerFactory

fun main() {
    val database = Database(mapHikariConfig(DatabaseConfig()))
    database.migrate()

    embeddedServer(Netty, port = 8080, module = Application::myApplicationModule).start(wait = true)

}

internal val log = LoggerFactory.getLogger("no.nav.emottak.cpa.App")
fun Application.myApplicationModule() {
    install(ContentNegotiation) {
        json()
    }
    routing {
        get("/cpa/{$CPA_ID}") {
            val cpaId = call.parameters[CPA_ID] ?: throw BadRequestException("Mangler $CPA_ID")
            val cpa = getCpa(cpaId) ?: throw NotFoundException("Fant ikke CPA")
            call.respond(cpa)
        }

        post("cpa/validate") {
            val validateRequest = call.receive(Header::class)
            try {
                getCpa(validateRequest.cpaId)!!.validate(validateRequest)
            } catch (cpaEx: CpaValidationException) {
                log.info(cpaEx.message, cpaEx) // TODO kanskje logge CPA IDen som indekset verdi til kibana
                call.respond(HttpStatusCode.OK, ValidationResult(false))
            }
            call.respond(HttpStatusCode.OK,ValidationResult(true))
        }

        get("/cpa/{$CPA_ID}/party/{$PARTY_TYPE}/{$PARTY_ID}/encryption/certificate") {
            val cpaId = call.parameters[CPA_ID] ?: throw BadRequestException("Mangler $CPA_ID")
            val partyType = call.parameters[PARTY_TYPE] ?: throw BadRequestException("Mangler $PARTY_TYPE")
            val partyId = call.parameters[PARTY_ID] ?: throw BadRequestException("Mangler $PARTY_ID")
            val cpa = getCpa(cpaId) ?: throw NotFoundException("Ingen CPA med ID $cpaId funnet")
            val partyInfo = cpa.getPartyInfoByTypeAndID(partyType, partyId)
            call.respond(partyInfo.getCertificateForEncryption())
        }

        post("/signing/certificate") {
            val signatureDetailsRequest = call.receive(SignatureDetailsRequest::class)
            val cpa = getCpa(signatureDetailsRequest.cpaId) ?: throw NotFoundException("Ingen CPA med ID ${signatureDetailsRequest.cpaId} funnet")
            val partyInfo = cpa.getPartyInfoByTypeAndID(signatureDetailsRequest.partyType, signatureDetailsRequest.partyId)

            call.respond(partyInfo.getCertificateForSignatureValidation(
                signatureDetailsRequest.role, signatureDetailsRequest.service, signatureDetailsRequest.action))
        }
    }
}

private const val CPA_ID = "cpaId"
private const val PARTY_TYPE = "partyType"
private const val PARTY_ID = "partyId"
private const val ROLE = "role"
private const val SERVICE = "service"
private const val ACTION = "action"
