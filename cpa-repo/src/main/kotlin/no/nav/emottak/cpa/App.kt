package no.nav.emottak.cpa

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.emottak.cpa.config.DatabaseConfig
import no.nav.emottak.cpa.config.mapHikariConfig
import no.nav.emottak.cpa.validation.validate
import no.nav.emottak.melding.model.Error
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.Processing
import no.nav.emottak.melding.model.SignatureDetails
import no.nav.emottak.melding.model.SignatureDetailsRequest
import no.nav.emottak.melding.model.ValidationResponse
import no.nav.emottak.util.createX509Certificate
import no.nav.emottak.util.marker
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
                val cpa = getCpa(validateRequest.cpaId)!!
                cpa.validate(validateRequest)
                val partyInfo = cpa.getPartyInfoByTypeAndID(validateRequest.from.partyId)
                val encryptionCertificate = partyInfo.getCertificateForEncryption()
                val signingCertificate =partyInfo.getCertificateForSignatureValidation(validateRequest.from.role,validateRequest.service,validateRequest.action)

                call.respond(HttpStatusCode.OK, ValidationResponse(Processing(signingCertificate,encryptionCertificate)))

            } catch (cpaEx: CpaValidationException) {
                log.warn(validateRequest.marker(), cpaEx.message, cpaEx)
                call.respond(HttpStatusCode.OK, ValidationResponse(processing = null, listOf(no.nav.emottak.melding.model.Error(ErrorCode.OTHER_XML,"CPA validation has failed")) ))
            }

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
            try {
                val partyInfo = cpa.getPartyInfoByTypeAndID(signatureDetailsRequest.partyType, signatureDetailsRequest.partyId)
                val signatureDetails = partyInfo.getCertificateForSignatureValidation(
                    signatureDetailsRequest.role, signatureDetailsRequest.service, signatureDetailsRequest.action)
                // TODO Strengere signatursjekk. Nå er den snill og resultatet logges bare
                runCatching {
                    createX509Certificate(signatureDetails.certificate).validate()
                }.onFailure {
                    log.warn(signatureDetailsRequest.marker(), "Signatursjekk feilet", it)
                }
                call.respond(signatureDetails)
            } catch (ex: CpaValidationException) {
                log.warn(signatureDetailsRequest.marker(), ex.message, ex)
                call.respond(HttpStatusCode.BadRequest, ex.localizedMessage)
            }
        }
    }
}

private const val CPA_ID = "cpaId"
private const val PARTY_TYPE = "partyType"
private const val PARTY_ID = "partyId"
private const val ROLE = "role"
private const val SERVICE = "service"
private const val ACTION = "action"
