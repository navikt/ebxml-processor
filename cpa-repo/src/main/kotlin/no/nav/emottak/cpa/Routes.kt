package no.nav.emottak.cpa

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.emottak.cpa.feil.CpaValidationException
import no.nav.emottak.cpa.persistence.CPARepository
import no.nav.emottak.cpa.persistence.gammel.PartnerRepository
import no.nav.emottak.cpa.validation.validate
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.melding.model.EbmsProcessing
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.Feil
import no.nav.emottak.melding.model.PayloadProcessing
import no.nav.emottak.melding.model.SignatureDetailsRequest
import no.nav.emottak.melding.model.ValidationRequest
import no.nav.emottak.melding.model.ValidationResult
import no.nav.emottak.util.createX509Certificate
import no.nav.emottak.util.marker
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import java.time.Instant
import java.time.temporal.ChronoUnit

fun Route.getCPA(cpaRepository: CPARepository): Route = get("/cpa/{$CPA_ID}") {
    val cpaId = call.parameters[CPA_ID] ?: throw BadRequestException("Mangler $CPA_ID")
    val cpa = cpaRepository.findCpa(cpaId) ?: throw NotFoundException("Fant ikke CPA")
    call.respond(cpa.asText())
}

fun Route.deleteAllCPA(cpaRepository: CPARepository): Route = get("/cpa/deleteAll") {
    call.respond("Number of deleted cpa ${cpaRepository.deleteAll()}")
}

fun Route.partnerId(partnerRepository: PartnerRepository, cpaRepository: CPARepository): Route = get("/partner/{$HER_ID}") {
    val partner = partnerRepository.findPartners("nav:qass:35065")
    call.respond("$partner")
}

fun Route.deleteCpa(cpaRepository: CPARepository): Route = delete("/cpa/delete/{$CPA_ID}") {
    val cpaId = call.parameters[CPA_ID] ?: throw BadRequestException("Mangler $CPA_ID")
    cpaRepository.deleteCpa(cpaId) ?: throw NotFoundException("Fant ikke CPA")
    call.respond("$cpaId slettet!")
}

fun Route.getTimeStamps(cpaRepository: CPARepository): Route = get("/cpa/timestamps") {
    log.info("Timestamps")
    call.respond(
        HttpStatusCode.OK,
        cpaRepository.findCpaTimestamps(
            call.request.headers[CPA_IDS]
                .let {
                    if (!it.isNullOrBlank()) {
                        it.split(",")
                    } else {
                        emptyList()
                    }
                }
        )
    )
}
fun Route.getTimeStampsLatest(cpaRepository: CPARepository) = get("/cpa/timestamps/latest") {
    log.info("Timestamplatest")
    call.respond(
        HttpStatusCode.OK,
        cpaRepository.findLatestUpdatedCpaTimestamp()
    )
}

fun Route.postCpa(cpaRepository: CPARepository) = post("/cpa") {
    log.info("post-cpa")
    val cpaString = call.receive<String>()
    // TODO en eller annen form for validering av CPA
    val updatedDate = call.request.headers["updated_date"].let {
        if (it.isNullOrBlank()) {
            Instant.now().truncatedTo(ChronoUnit.SECONDS)
        }
        Instant.parse(it).truncatedTo(ChronoUnit.SECONDS) // TODO feilhåndter
    }
    val cpa = xmlMarshaller.unmarshal(cpaString, CollaborationProtocolAgreement::class.java)

    if (call.request.headers["upsert"].equals("true")) {
        cpaRepository.upsertCpa(
            CPARepository.CpaDbEntry(
                cpa.cpaid,
                cpa,
                updatedDate,
                Instant.now()
            )
        ).also {
            log.info("Added CPA $it to repo")
            call.respond(HttpStatusCode.OK, "Added CPA $it to repo")
        }
    } else {
        cpaRepository.putCpa(
            CPARepository.CpaDbEntry(
                cpa.cpaid,
                cpa,
                updatedDate,
                Instant.now()
            )
        ).also {
            log.info("Added CPA $it to repo")
            call.respond(HttpStatusCode.OK, "Added CPA $it to repo")
        }
    }
}

fun Route.validateCpa(cpaRepository: CPARepository) = post("/cpa/validate/{$CONTENT_ID}") {
    val validateRequest = call.receive(ValidationRequest::class)
    try {
        val cpa = cpaRepository.findCpa(validateRequest.cpaId) ?: throw NotFoundException("Fant ikke CPA")
        cpa.validate(validateRequest) // Delivery Filure
        val partyInfo = cpa.getPartyInfoByTypeAndID(validateRequest.addressing.from.partyId) // delivery Failure
        val encryptionCertificate = partyInfo.getCertificateForEncryption() // Security Failure
        val signingCertificate = partyInfo.getCertificateForSignatureValidation(
            validateRequest.addressing.from.role,
            validateRequest.addressing.service,
            validateRequest.addressing.action
        ) // Security Failure
        runCatching {
            createX509Certificate(signingCertificate.certificate).validate()
        }.onFailure {
            log.warn(validateRequest.marker(), "Validation feilet i sertifikat sjekk", it)
            throw it
        }
        call.respond(
            HttpStatusCode.OK,
            ValidationResult(
                EbmsProcessing(),
                PayloadProcessing(
                    signingCertificate,
                    encryptionCertificate,
                    cpaRepository.getProcessConfig(
                        validateRequest.addressing.from.role,
                        validateRequest.addressing.service,
                        validateRequest.addressing.action
                    ) ?: throw NotFoundException("No Processing Config found")
                    // TODO Bør kunne processeres uten config? I den idelle verden burde ikke config eksistere.
                )
            )
        )
    } catch (ebmsEx: EbmsException) {
        log.warn(validateRequest.marker(), ebmsEx.message, ebmsEx)
        call.respond(HttpStatusCode.OK, ValidationResult(error = listOf(Feil(ebmsEx.errorCode, ebmsEx.descriptionText, ebmsEx.severity))))
    } catch (ex: NotFoundException) {
        log.warn(validateRequest.marker(), "${ex.message}")
        call.respond(HttpStatusCode.OK, ValidationResult(error = listOf(Feil(ErrorCode.DELIVERY_FAILURE, "Unable to find CPA"))))
    } catch (ex: Exception) {
        log.error(validateRequest.marker(), ex.message, ex)
        call.respond(HttpStatusCode.OK, ValidationResult(error = listOf(Feil(ErrorCode.UNKNOWN, "Unexpected error during cpa validation"))))
    }
}

fun Route.getCertificate(cpaRepository: CPARepository) = get("/cpa/{$CPA_ID}/party/{$PARTY_TYPE}/{$PARTY_ID}/encryption/certificate") {
    val cpaId = call.parameters[CPA_ID] ?: throw BadRequestException("Mangler $CPA_ID")
    val partyType = call.parameters[PARTY_TYPE] ?: throw BadRequestException("Mangler $PARTY_TYPE")
    val partyId = call.parameters[PARTY_ID] ?: throw BadRequestException("Mangler $PARTY_ID")
    val cpa = cpaRepository.findCpa(cpaId) ?: throw NotFoundException("Ingen CPA med ID $cpaId funnet")
    val partyInfo = cpa.getPartyInfoByTypeAndID(partyType, partyId)
    call.respond(partyInfo.getCertificateForEncryption())
}

fun Route.signingCertificate(cpaRepository: CPARepository) = post("/signing/certificate") {
    val signatureDetailsRequest = call.receive(SignatureDetailsRequest::class)
    val cpa = cpaRepository.findCpa(signatureDetailsRequest.cpaId) ?: throw NotFoundException("Ingen CPA med ID ${signatureDetailsRequest.cpaId} funnet")
    try {
        val partyInfo = cpa.getPartyInfoByTypeAndID(signatureDetailsRequest.partyType, signatureDetailsRequest.partyId)
        val signatureDetails = partyInfo.getCertificateForSignatureValidation(
            signatureDetailsRequest.role,
            signatureDetailsRequest.service,
            signatureDetailsRequest.action
        )
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

fun Route.partnerID(cpaRepository: CPARepository) = get("/cpa/partnerId/{$HER_ID}") {
}

private const val CPA_ID = "cpaId"
private const val CPA_IDS = "cpaIds"
private const val PARTY_TYPE = "partyType"
private const val PARTY_ID = "partyId"
private const val CONTENT_ID = "contentId"
private const val HER_ID = "herId"
private const val ROLE = "role"
private const val SERVICE = "service"
private const val ACTION = "action"
