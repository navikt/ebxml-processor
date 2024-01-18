package no.nav.emottak.cpa

import com.zaxxer.hikari.HikariConfig
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
import java.time.Instant
import no.nav.emottak.cpa.config.DatabaseConfig
import no.nav.emottak.cpa.config.mapHikariConfig
import no.nav.emottak.cpa.feil.CpaValidationException
import no.nav.emottak.cpa.persistence.CPARepository
import no.nav.emottak.cpa.validation.validate
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.Feil
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.Processing
import no.nav.emottak.melding.model.SignatureDetailsRequest
import no.nav.emottak.melding.model.ValidationResult
import no.nav.emottak.util.createX509Certificate
import no.nav.emottak.util.marker
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import org.slf4j.LoggerFactory

fun main() {
    embeddedServer(Netty, port = 8080, module = cpaApplicationModule(mapHikariConfig(DatabaseConfig()))).start(wait = true)
}

fun cpaApplicationModule(dbConfig: HikariConfig): Application.() -> Unit {
    return {
        val database = Database(dbConfig)
        database.migrate()
        val cpaRepository = CPARepository(database)
        install(ContentNegotiation) {
            json()
        }
        routing {
            get("/cpa/{$CPA_ID}") {
                val cpaId = call.parameters[CPA_ID] ?: throw BadRequestException("Mangler $CPA_ID")
//            val cpa = getCpa(cpaId) ?: throw NotFoundException("Fant ikke CPA")
                val cpa = cpaRepository.findCpa(cpaId) ?: throw NotFoundException("Fant ikke CPA")
                call.respond(cpa)
            }

            get("/cpa/timestamps") {
                println("Timestamps")
                call.respond(HttpStatusCode.OK,
                    cpaRepository.findCpaTimestamps(
                        call.request.headers[CPA_IDS]
                            .let {
                                if(!it.isNullOrBlank()) it.split(",")
                                else emptyList()
                            }
                    )
                )
            }

            get("/cpa/timestamps/latest") {
                println("Timestamplatest")
                call.respond(HttpStatusCode.OK,
                    cpaRepository.findLatestUpdatedCpaTimestamp()
                )
            }

            post("/cpa") {
                val cpaString = call.receive<String>()
                //TODO en eller annen form for validering av CPA
                val updatedDate = call.request.headers["updated_date"].let {
                    if (it.isNullOrBlank()){
                        Instant.now()
                    }
                    Instant.parse(it) // TODO feilhåndter
                }
                val cpa = xmlMarshaller.unmarshal(cpaString, CollaborationProtocolAgreement::class.java)
                cpaRepository.putCpa(CPARepository.CpaDbEntry(cpa.cpaid, cpa,
                    updatedDate,
                    Instant.now())).also {
                    log.info("Added CPA $it to repo")
                    call.respond(HttpStatusCode.OK, "Added CPA $it to repo")
                }
            }

            post("/cpa/validate/{$CONTENT_ID}") {
                val validateRequest = call.receive(Header::class)
                try {
//                val cpa = getCpa(validateRequest.cpaId)!!
                    val cpa = cpaRepository.findCpa(validateRequest.cpaId) ?: throw NotFoundException("Fant ikke CPA")
                    cpa.validate(validateRequest) // Delivery Filure
                    val partyInfo = cpa.getPartyInfoByTypeAndID(validateRequest.from.partyId) // delivery Failure
                    val encryptionCertificate = partyInfo.getCertificateForEncryption()  // Security Failure
                    val signingCertificate = partyInfo.getCertificateForSignatureValidation(
                        validateRequest.from.role, validateRequest.service, validateRequest.action) //Security Failure
                    runCatching {
                        createX509Certificate(signingCertificate.certificate).validate()
                    }.onFailure {
                        log.warn(validateRequest.marker(), "Validation feilet i sertifikat sjekk", it)
                        throw it
                    }

                    call.respond(HttpStatusCode.OK, ValidationResult(Processing(signingCertificate,encryptionCertificate)))

                } catch (ebmsEx: EbmsException) {
                    log.warn(validateRequest.marker(), ebmsEx.message, ebmsEx)
                    call.respond(HttpStatusCode.OK, ValidationResult(processing = null, listOf( Feil(ebmsEx.errorCode, ebmsEx.descriptionText, ebmsEx.severity))))
                } catch (ex: NotFoundException) {
                    log.warn(validateRequest.marker(), "${ex.message}")
                    call.respond(HttpStatusCode.OK, ValidationResult(processing = null, listOf( Feil(ErrorCode.DELIVERY_FAILURE,"Unable to find CPA"))))
                } catch (ex: Exception) {
                    log.error(validateRequest.marker(),ex.message,ex)
                    call.respond(HttpStatusCode.OK,ValidationResult(processing = null, listOf(Feil(ErrorCode.UNKNOWN,"Unexpected error during cpa validation"))))
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
}
internal val log = LoggerFactory.getLogger("no.nav.emottak.cpa.App")

private const val CPA_ID = "cpaId"
private const val CPA_IDS = "cpaIds"
private const val PARTY_TYPE = "partyType"
private const val PARTY_ID = "partyId"
private const val CONTENT_ID ="contentId"
private const val ROLE = "role"
private const val SERVICE = "service"
private const val ACTION = "action"
