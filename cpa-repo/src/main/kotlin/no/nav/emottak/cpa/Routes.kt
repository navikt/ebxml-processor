package no.nav.emottak.cpa

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.cpa.auth.AZURE_AD_AUTH
import no.nav.emottak.cpa.feil.CpaValidationException
import no.nav.emottak.cpa.feil.MultiplePartnerException
import no.nav.emottak.cpa.feil.PartnerNotFoundException
import no.nav.emottak.cpa.persistence.CPARepository
import no.nav.emottak.cpa.persistence.gammel.PartnerRepository
import no.nav.emottak.cpa.validation.MessageDirection
import no.nav.emottak.cpa.validation.partyInfoHasRoleServiceActionCombo
import no.nav.emottak.cpa.validation.validate
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.message.ebxml.PartyTypeEnum
import no.nav.emottak.message.model.Direction.IN
import no.nav.emottak.message.model.Direction.OUT
import no.nav.emottak.message.model.EbmsProcessing
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Feil
import no.nav.emottak.message.model.PayloadProcessing
import no.nav.emottak.message.model.SignatureDetailsRequest
import no.nav.emottak.message.model.ValidationRequest
import no.nav.emottak.message.model.ValidationResult
import no.nav.emottak.util.createX509Certificate
import no.nav.emottak.util.getEnvVar
import no.nav.emottak.util.marker
import no.nav.security.token.support.v2.TokenValidationContextPrincipal
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import java.util.Date

fun Route.whoAmI(): Route = get("/whoami") {
    log.info("whoAmI")
    val principal: TokenValidationContextPrincipal? = call.authentication.principal()
    if (principal != null && "dev-fss" == getEnvVar("NAIS_CLUSTER_NAME", "dev-fss")) {
        call.respond("Gyldig: " + principal.context.getJwtToken(AZURE_AD_AUTH)?.encodedToken)
    } else {
        call.respond(
            principal?.context?.anyValidClaims?.getStringClaim("NAVident") ?: "NULL"
        )
    }
}

fun Route.getCPA(cpaRepository: CPARepository): Route = get("/cpa/{$CPA_ID}") {
    val cpaId = call.parameters[CPA_ID] ?: throw BadRequestException("Mangler $CPA_ID")
    val cpa = cpaRepository.findCpa(cpaId) ?: throw NotFoundException("Fant ikke CPA $CPA_ID")
    call.respond(cpa.asText())
}

fun Route.deleteAllCPA(cpaRepository: CPARepository): Route = get("/cpa/deleteAll") {
    call.respond("Number of deleted cpa ${cpaRepository.deleteAll()}")
}

fun Route.partnerId(partnerRepository: PartnerRepository, cpaRepository: CPARepository): Route =
    get("/partner/her/{$HER_ID}") {
        val herId = call.parameters[HER_ID] ?: throw BadRequestException("Mangler $HER_ID")
        val role = call.request.queryParameters[ROLE] ?: throw BadRequestException("Mangler $ROLE")
        val service = call.request.queryParameters[SERVICE] ?: throw BadRequestException("Mangler $SERVICE")
        val action = call.request.queryParameters[ACTION] ?: throw BadRequestException("Mangler $ACTION")
        val now = Date()

        runCatching {
            val sisteOppdatertCpa = cpaRepository.cpaByHerId(herId).onEach {
                log.info(xmlMarshaller.marshal(it.value))
            }.filter {
                it.value.start.before(now)
                it.value.end.after(now)
            }.filter {
                // filter out alle med revokert sertifikat
                val partyInfo = it.value.getPartyInfoByTypeAndID(PartyTypeEnum.HER.type, herId)
                runCatching {
                    createX509Certificate(partyInfo.getCertificateForEncryption()).validate()
                }.isSuccess
            }.filter {
                // Sjekker at partner kan motta angitt melding
                val partyInfo = it.value.getPartyInfoByTypeAndID(PartyTypeEnum.HER.type, herId)
                runCatching {
                    partyInfoHasRoleServiceActionCombo(partyInfo, role, service, action, MessageDirection.RECEIVE)
                }.isSuccess
            }.maxBy {
                it.key
            }.value
            partnerRepository.findPartners(sisteOppdatertCpa.cpaid)
        }.onSuccess {
            log.info("Partner $it funnet for HER ID $herId")
            call.respond(HttpStatusCode.OK, it)
        }.onFailure {
            log.warn("Feil ved henting av $PARTNER_ID", it)
            when (it) {
                is MultiplePartnerException -> call.respond(
                    HttpStatusCode.Conflict,
                    "Fant multiple $PARTNER_ID for $HER_ID $herId. Dette er en ugyldig tilstand."
                )

                is PartnerNotFoundException -> call.respond(
                    HttpStatusCode.NotFound,
                    "Fant ikke $PARTNER_ID for $HER_ID $herId"
                )

                is CpaValidationException -> call.respond(
                    HttpStatusCode.BadRequest,
                    "Role, Service, Action kombinasjon ugyldig. [${it.message}]"
                )

                else -> call.respond(HttpStatusCode.NotFound, "Fant ikke $PARTNER_ID for $HER_ID $herId")
            }
        }
    }

fun Route.deleteCpa(cpaRepository: CPARepository): Route = delete("/cpa/delete/{$CPA_ID}") {
    val cpaId = call.parameters[CPA_ID] ?: throw BadRequestException("Mangler $CPA_ID")
    cpaRepository.deleteCpa(cpaId)
    "$cpaId slettet!".let {
        log.info(it)
        call.respond(it)
    }
}

fun Route.getTimeStamps(cpaRepository: CPARepository): Route = get("/cpa/timestamps") {
    log.info("Timestamps")
    call.respond(
        HttpStatusCode.OK,
        cpaRepository.findCpaTimestamps(
            withContext(Dispatchers.IO) {
                return@withContext call.request.headers[CPA_IDS]
                    .let {
                        if (!it.isNullOrBlank()) {
                            it.split(",")
                        } else {
                            emptyList()
                        }
                    }
            }
        )
    )
}

fun Route.getTimeStampsLatest(cpaRepository: CPARepository) = get("/cpa/timestamps/latest") {
    log.info("Timestamplatest")
    val latestTimestamp = withContext(Dispatchers.IO) {
        cpaRepository.findLatestUpdatedCpaTimestamp()
    }
    when (latestTimestamp) {
        null -> call.respond(HttpStatusCode.NotFound, "No timestamps found")
        else -> call.respond(HttpStatusCode.OK, latestTimestamp)
    }
}

fun Route.postCpa(cpaRepository: CPARepository) = post("/cpa") {
    log.info("post-cpa")
    val cpaString = call.receive<String>()
    // TODO en eller annen form for validering av CPA
    val updatedDateString: String? = call.request.headers["updated_date"]
    val cpa = xmlMarshaller.unmarshal(cpaString, CollaborationProtocolAgreement::class.java)

    cpaRepository
        .updateOrInsert(CPARepository.CpaDbEntry(cpa, updatedDateString))
        .also {
            log.info("Updated or Inserted CPA $it in repo")
            call.respond(HttpStatusCode.OK, "Updated or Inserted CPA $it in repo")
        }
}

fun Route.validateCpa(cpaRepository: CPARepository) = post("/cpa/validate/{$CONTENT_ID}") {
    val validateRequest = call.receive(ValidationRequest::class)
    try {
        log.info(validateRequest.marker(), "Validerer ebms mot CPA")
        val cpa = cpaRepository.findCpa(validateRequest.cpaId)
            ?: throw NotFoundException("Fant ikke CPA (${validateRequest.cpaId})")
        cpa.validate(validateRequest) // Delivery Failure
        val partyInfo = cpa.getPartyInfoByTypeAndID(validateRequest.addressing.from.partyId) // Delivery Failure

        val encryptionCertificate = when (validateRequest.direction) {
            IN -> partyInfo.getCertificateForEncryption() // Security Failure
            OUT ->
                cpa
                    .getPartyInfoByTypeAndID(validateRequest.addressing.to.partyId)
                    .getCertificateForEncryption()
        }

        val signingCertificate = partyInfo.getCertificateForSignatureValidation(
            validateRequest.addressing.from.role,
            validateRequest.addressing.service,
            validateRequest.addressing.action
        ) // Security Failure
        runCatching {
            createX509Certificate(signingCertificate.certificate).validate()
        }.onFailure {
            log.error(validateRequest.marker(), "Validation feilet i sertifikat sjekk", it)
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
                    )
                )
            )
        )
    } catch (ebmsEx: EbmsException) {
        log.error(validateRequest.marker(), ebmsEx.message, ebmsEx)
        call.respond(
            HttpStatusCode.OK,
            ValidationResult(error = ebmsEx.feil)
        )
    } catch (ex: NotFoundException) {
        log.error(validateRequest.marker(), "${ex.message}")
        call.respond(
            HttpStatusCode.OK,
            ValidationResult(error = listOf(Feil(ErrorCode.DELIVERY_FAILURE, "${ex.message}")))
        )
    } catch (ex: Exception) {
        log.error(validateRequest.marker(), ex.message, ex)
        call.respond(
            HttpStatusCode.OK,
            ValidationResult(error = listOf(Feil(ErrorCode.UNKNOWN, "Unexpected error during cpa validation")))
        )
    }
}

fun Route.getCertificate(cpaRepository: CPARepository) =
    get("/cpa/{$CPA_ID}/party/{$PARTY_TYPE}/{$PARTY_ID}/encryption/certificate") {
        val cpaId = call.parameters[CPA_ID] ?: throw BadRequestException("Mangler $CPA_ID")
        val partyType = call.parameters[PARTY_TYPE] ?: throw BadRequestException("Mangler $PARTY_TYPE")
        val partyId = call.parameters[PARTY_ID] ?: throw BadRequestException("Mangler $PARTY_ID")
        val cpa = cpaRepository.findCpa(cpaId) ?: throw NotFoundException("Ingen CPA med ID $cpaId funnet")
        val partyInfo = cpa.getPartyInfoByTypeAndID(partyType, partyId)
        call.respond(partyInfo.getCertificateForEncryption())
    }

fun Route.signingCertificate(cpaRepository: CPARepository) = post("/signing/certificate") {
    val signatureDetailsRequest = call.receive(SignatureDetailsRequest::class)
    val cpa = cpaRepository.findCpa(signatureDetailsRequest.cpaId)
        ?: throw NotFoundException("Ingen CPA med ID ${signatureDetailsRequest.cpaId} funnet")
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

fun Routing.registerHealthEndpoints(
    collectorRegistry: PrometheusMeterRegistry,
    cpaRepository: CPARepository
) {
    get("/internal/health/liveness") {
        call.respondText("Liveness OK")
    }
    get("/internal/health/readiness") {
        runCatching {
            cpaRepository.findLatestUpdatedCpaTimestamp()
        }.onSuccess {
            call.respond(HttpStatusCode.OK, "Readiness OK")
        }.onFailure {
            log.warn("Readiness not OK! Reason: ${it.localizedMessage}", it)
            call.respond(HttpStatusCode.InternalServerError, "Readiness not OK! Reason: ${it.localizedMessage}")
        }
    }
    get("/prometheus") {
        call.respond(collectorRegistry.scrape())
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
private const val PARTNER_ID = "partnerId"
private const val ROLE = "role"
private const val SERVICE = "service"
private const val ACTION = "action"
