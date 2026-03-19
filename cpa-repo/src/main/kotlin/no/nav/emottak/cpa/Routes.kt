package no.nav.emottak.cpa

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authentication
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import no.nav.emottak.cpa.auth.AZURE_AD_AUTH
import no.nav.emottak.cpa.configuration.config
import no.nav.emottak.cpa.feil.CpaValidationException
import no.nav.emottak.cpa.feil.MultiplePartnerException
import no.nav.emottak.cpa.feil.PartnerNotFoundException
import no.nav.emottak.cpa.model.Certificate
import no.nav.emottak.cpa.model.CommunicationParty
import no.nav.emottak.cpa.persistence.CPARepository
import no.nav.emottak.cpa.persistence.gammel.PartnerRepository
import no.nav.emottak.cpa.util.EventRegistrationService
import no.nav.emottak.cpa.validation.MessageDirection
import no.nav.emottak.cpa.validation.partyInfoHasRoleServiceActionCombo
import no.nav.emottak.cpa.validation.validate
import no.nav.emottak.message.ebxml.EbXMLConstants.ACKNOWLEDGMENT_ACTION
import no.nav.emottak.message.ebxml.EbXMLConstants.EBMS_SERVICE_URI
import no.nav.emottak.message.ebxml.EbXMLConstants.MESSAGE_ERROR_ACTION
import no.nav.emottak.message.ebxml.PartyTypeEnum
import no.nav.emottak.message.exception.EbmsException
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Feil
import no.nav.emottak.message.model.MessagingCharacteristicsRequest
import no.nav.emottak.message.model.MessagingCharacteristicsResponse
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadProcessing
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.message.model.SignatureDetailsRequest
import no.nav.emottak.message.model.ValidationRequest
import no.nav.emottak.message.model.ValidationResult
import no.nav.emottak.message.xml.getDocumentBuilder
import no.nav.emottak.message.xml.xmlMarshaller
import no.nav.emottak.util.createX509Certificate
import no.nav.emottak.util.decodeBase64
import no.nav.emottak.util.isToday
import no.nav.emottak.util.marker
import no.nav.emottak.utils.common.model.EbmsProcessing
import no.nav.emottak.utils.environment.getEnvVar
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.serialization.toEventDataJson
import no.nav.security.token.support.v3.TokenValidationContextPrincipal
import org.apache.xml.security.algorithms.MessageDigestAlgorithm
import org.apache.xml.security.signature.XMLSignature
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import java.util.Date
import kotlin.uuid.Uuid

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

    val cpa = cpaRepository.findCpa(cpaId) // ?: throw NotFoundException("Fant ikke CPA $CPA_ID")
    if (cpa == null) {
        log.info("CPA NOT FOUND: $cpa")
        val document = getDocumentBuilder().parse(java.io.File("src/test/resources/oppgjorsmelding/payloadmessage.xml"))
        val ebmsDocument = EbmsDocument(Uuid.random().toString(), document, attachments)
        log.info("${ebmsDocument.messageHeader().from.partyId.firstOrNull()?.value}")

        call.respond(HttpStatusCode.NotFound)
    } else {
        call.respondText(
            contentType = ContentType.Text.Xml,
            text = cpa.asText()
        )
    }
}

private val attachments = listOf(
    Payload(
        bytes = "Test attachment content".toByteArray(),
        contentType = "text/plain",
        contentId = "attachment1"
    )
)

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
            partnerRepository.findPartnerId(sisteOppdatertCpa.cpaid)
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

fun Route.getTimeStampsDeprecated(): Route = get("/cpa/timestamps") {
    log.warn("Timestamps last_updated (deprecated endpoint)")
    call.respondRedirect("/cpa/timestamps/last_updated", permanent = true)
}

fun Route.getTimeStamps(cpaRepository: CPARepository): Route = get("/cpa/timestamps/last_updated") {
    log.info("Timestamps last_updated")
    call.respond(
        HttpStatusCode.OK,
        cpaRepository.findTimestampsCpaUpdated(
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

fun Route.getTimeStampsLatestDeprecated() = get("/cpa/timestamps/latest") {
    log.warn("Timestamps latest last_updated (deprecated endpoint)")
    call.respondRedirect("/cpa/timestamps/last_updated/latest", permanent = true)
}

fun Route.getTimeStampsLatest(cpaRepository: CPARepository) = get("/cpa/timestamps/last_updated/latest") {
    log.info("Timestamps latest last_updated")
    val latestTimestamp = withContext(Dispatchers.IO) {
        cpaRepository.findTimestampCpaLatestUpdated()
    }
    when (latestTimestamp) {
        null -> call.respond(HttpStatusCode.NotFound, "No timestamps found")
        else -> call.respond(HttpStatusCode.OK, latestTimestamp)
    }
}

fun Route.getTimeStampsLastUsed(cpaRepository: CPARepository): Route = get("/cpa/timestamps/last_used") {
    log.info("Timestamps last_used")
    call.respond(
        HttpStatusCode.OK,
        cpaRepository.findTimestampsCpaLastUsed()
    )
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

fun Route.validateCpa(
    cpaRepository: CPARepository,
    partnerRepository: PartnerRepository,
    eventRegistrationService: EventRegistrationService
) = post("/cpa/validate/{$REQUEST_ID}") {
    val validateRequest = call.receive(ValidationRequest::class)

    val requestId = call.parameters[REQUEST_ID] ?: throw BadRequestException("Mangler $REQUEST_ID")

    try {
        log.info(validateRequest.marker(), "Validerer ebms mot CPA")
        val (cpa, lastUsed) = cpaRepository.findCpaAndLastUsed(validateRequest.cpaId)
        if (cpa == null) throw NotFoundException("Fant ikke CPA (${validateRequest.cpaId})")
        if (!lastUsed.isToday() && !cpaRepository.updateCpaLastUsed(validateRequest.cpaId)) {
            log.warn(validateRequest.marker(), "Feilet med å oppdatere last_used for CPA '${validateRequest.cpaId}'")
        }
        if (!validateRequest.isSignalMessage()) {
            cpa.validate(validateRequest)
        } // Delivery Failure

        val fromParty = cpa.getPartyInfoByTypeAndID(validateRequest.addressing.from.partyId) // Delivery Failure
        val toParty = cpa.getPartyInfoByTypeAndID(validateRequest.addressing.to.partyId) // Delivery Failure
        val encryptionCertificate = toParty.getCertificateForEncryption()

        val signingCertificate = fromParty.getCertificateForSignatureValidation(
            validateRequest.addressing.from.role,
            validateRequest.addressing.service,
            validateRequest.addressing.action
        ) // Security Failure

        val signalEmails = toParty.getSignalEmailAddress(validateRequest)
        val receiverEmails = toParty.getReceiveEmailAddress(validateRequest)

        runCatching {
            createX509Certificate(signingCertificate.certificate).validate()
        }.onFailure {
            log.error(validateRequest.marker(), "Validation feilet i sertifikat sjekk", it)
            throw it
        }

        val partnerId = runCatching { partnerRepository.findPartnerId(cpa.cpaid) }.getOrNull()

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
                ),
                signalEmails,
                receiverEmails,
                partnerId
            )
        )

        eventRegistrationService.registerEvent(
            EventType.MESSAGE_VALIDATED_AGAINST_CPA,
            validateRequest,
            requestId,
            Json.encodeToString(
                mapOf(EventDataType.SENDER_NAME.value to fromParty.partyName)
            )
        )
    } catch (ebmsEx: EbmsException) {
        eventRegistrationService.registerEvent(
            EventType.VALIDATION_AGAINST_CPA_FAILED,
            validateRequest,
            requestId,
            ebmsEx.toEventDataJson()
        )
        log.error(validateRequest.marker(), ebmsEx.message, ebmsEx)
        call.respond(
            HttpStatusCode.OK,
            ValidationResult(error = ebmsEx.feil)
        )
    } catch (ex: NotFoundException) {
        eventRegistrationService.registerEvent(
            EventType.VALIDATION_AGAINST_CPA_FAILED,
            validateRequest,
            requestId,
            ex.toEventDataJson()
        )
        log.error(validateRequest.marker(), "${ex.message}")
        call.respond(
            HttpStatusCode.OK,
            ValidationResult(error = listOf(Feil(ErrorCode.DELIVERY_FAILURE, "${ex.message}")))
        )
    } catch (ex: Exception) {
        eventRegistrationService.registerEvent(
            EventType.VALIDATION_AGAINST_CPA_FAILED,
            validateRequest,
            requestId,
            ex.toEventDataJson()
        )

        log.error(validateRequest.marker(), ex.message, ex)
        call.respond(
            HttpStatusCode.OK,
            ValidationResult(error = listOf(Feil(ErrorCode.UNKNOWN, "Unexpected error during cpa validation")))
        )
    }
}

private fun ValidationRequest.isSignalMessage(): Boolean = this.addressing.service == EBMS_SERVICE_URI &&
    (this.addressing.action == MESSAGE_ERROR_ACTION || this.addressing.action == ACKNOWLEDGMENT_ACTION)

fun Route.getCertificate(cpaRepository: CPARepository) =
    get("/cpa/{$CPA_ID}/party/{$PARTY_TYPE}/{$PARTY_ID}/encryption/certificate") {
        val cpaId = call.parameters[CPA_ID] ?: throw BadRequestException("Mangler $CPA_ID")
        val partyType = call.parameters[PARTY_TYPE] ?: throw BadRequestException("Mangler $PARTY_TYPE")
        val partyId = call.parameters[PARTY_ID] ?: throw BadRequestException("Mangler $PARTY_ID")
        val cpa = cpaRepository.findCpa(cpaId) ?: throw NotFoundException("Ingen CPA med ID $cpaId funnet")
        val partyInfo = cpa.getPartyInfoByTypeAndID(partyType, partyId)
        call.respond(partyInfo.getCertificateForEncryption())
    }

fun Route.signingCertificate(cpaRepository: CPARepository, httpClient: HttpClient?) = post("/signing/certificate") {
    val signatureDetailsRequest = call.receive(SignatureDetailsRequest::class)
    val cpa = cpaRepository.findCpa(signatureDetailsRequest.cpaId) // TODO Parviz ?: throw NotFoundException("Ingen CPA med ID ${signatureDetailsRequest.cpaId} funnet")
    if (cpa == null) {
        val herid = signatureDetailsRequest.partyId // TODO: Sjekk for herid fins i request
        log.info("Partner herid $herid")
        try {
            val arSignCertificate = httpClient?.fetchARSignCertificate(herid)
            log.info("Partner Sign certificate from AR on herid $herid (${arSignCertificate?.certificateValue}, \n valid to: ${arSignCertificate?.validFrom} \n valid to: ${arSignCertificate?.validTo}")
            val validSignatureDetails = SignatureDetails(
                certificate = decodeBase64(arSignCertificate!!.certificateValue.toByteArray()),
                signatureAlgorithm = XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
                hashFunction = MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256
            )
            runCatching {
                createX509Certificate(validSignatureDetails.certificate).validate()
            }.onFailure {
                log.warn(signatureDetailsRequest.marker(), "Signatursjekk feilet", it)
            }
            call.respond(arSignCertificate as Any)
        } catch (ex: Exception) {
            log.error("Error while fetching arSignCertificate <$herid>", ex)
            call.respondText(ex.localizedMessage, ContentType.Text.Plain, HttpStatusCode.InternalServerError)
        }
    } else {
        try {
            val partyInfo =
                cpa.getPartyInfoByTypeAndID(signatureDetailsRequest.partyType, signatureDetailsRequest.partyId)
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
}

fun Route.getMessagingCharacteristics(cpaRepository: CPARepository, httpClient: HttpClient?) =
    post("/cpa/messagingCharacteristics") {
        val request = call.receive(MessagingCharacteristicsRequest::class)
        val cpa = cpaRepository.findCpa(request.cpaId) // ?: throw NotFoundException("CPA not found for ID ${request.cpaId}")
        if (cpa == null) {
            val herid = request.partyIds.firstOrNull()?.value.toString() // TODO: Sjekk for herid fins i request
            try {
                val communicationParty = httpClient?.fetchCommunicationParty(herid)
                log.info("CommunicationParty:      $communicationParty")
                log.info("Partner information from AR on herid $herid (${communicationParty?.ediAddress}, ${communicationParty?.organizationDetails?.organizationNumber}")
                // call.respond(HttpStatusCode.OK, communicationParty)
            } catch (ex: Exception) {
                log.error("Error while fetching communication party <$herid>", ex)
                call.respondText(ex.localizedMessage, ContentType.Text.Plain, HttpStatusCode.InternalServerError)
            }
        } else {
            val fromParty = cpa.getPartyInfoByTypeAndID(request.partyIds)
            val deliveryChannel = fromParty.getSendDeliveryChannel(request.role, request.service, request.action)
            log.info("fromParty:      ${fromParty.partyId.firstOrNull()?.value}")
            val response = MessagingCharacteristicsResponse(
                requestId = request.requestId,
                ackRequested = deliveryChannel.messagingCharacteristics.ackRequested,
                ackSignatureRequested = deliveryChannel.messagingCharacteristics.ackSignatureRequested,
                duplicateElimination = deliveryChannel.messagingCharacteristics.duplicateElimination
            )

            call.respond(response)
        }
    }

//
// fun Route.getValidationRequest(cpaRepository: CPARepository, httpClient: HttpClient?) =
//    post("/cpa/validationrequest") {
//        val request = call.receive(ValidationRequest::class)
//        val cpa = cpaRepository.findCpa(request.cpaId) // ?: throw NotFoundException("CPA not found for ID ${request.cpaId}")
//
//        log.info("CommunicationParty:      ${cpa?.partyInfo.firstOrNull()?.partyId}")
//        if (cpa == null) {
//            val herId = request.addressing.from.partyId.firstOrNull()?.value // TODO: Sjekk for herid fins i request
//            try {
//                val communicationParty = httpClient?.fetchCommunicationParty(herId)
//                log.info("CommunicationParty:      ${communicationParty}")
//                log.info("Partner information from AR on herid ${herId} (${communicationParty?.ediAddress}, ${communicationParty?.organizationDetails?.organizationNumber}")
//                //call.respond(HttpStatusCode.OK, communicationParty)
//            } catch (ex: Exception) {
//                log.error("Error while fetching communication party <$herId>", ex)
//                call.respondText(ex.localizedMessage, ContentType.Text.Plain, HttpStatusCode.InternalServerError)
//            }
//        } else {
//            log.error("Error while fetching communication party")
//        }
//    }

fun Route.getAdresseregisterHerID(httpClient: HttpClient) =
    get("/cpa/adresseregister/her/{$HER_ID}") {
        val herId = call.parameters[HER_ID] ?: throw BadRequestException("Mangler $HER_ID")
        try {
            val communicationParty = httpClient.fetchARHerId(herId)
            call.respond(HttpStatusCode.OK, communicationParty)
        } catch (ex: Exception) {
            log.error("Error while fetching communication party <$herId>", ex)
            call.respondText(ex.localizedMessage, ContentType.Text.Plain, HttpStatusCode.InternalServerError)
        }
    }
fun Route.getAdresseregisterData(httpClient: HttpClient) =
    get("/cpa/adresseregister/her/{$HER_ID}") {
        val herId = call.parameters[HER_ID] ?: throw BadRequestException("Mangler $HER_ID")
        try {
            val communicationParty = httpClient.fetchCommunicationParty(herId)
            call.respond(HttpStatusCode.OK, communicationParty)
        } catch (ex: Exception) {
            log.error("Error while fetching communication party <$herId>", ex)
            call.respondText(ex.localizedMessage, ContentType.Text.Plain, HttpStatusCode.InternalServerError)
        }
    }

fun Route.getARSignCertificate(httpClient: HttpClient) =
    get("/cpa/adresseregister/her/{$HER_ID}/signing") {
        val herId = call.parameters[HER_ID] ?: throw BadRequestException("Mangler $HER_ID")
        try {
            val certificate = httpClient.fetchARSignCertificate(herId)
            call.respond(HttpStatusCode.OK, certificate)
        } catch (ex: Exception) {
            log.error("Error while fetching signing certificate <$herId>", ex)
            call.respondText(ex.localizedMessage, ContentType.Text.Plain, HttpStatusCode.InternalServerError)
        }
    }

fun Route.getAREncryptCertificate(httpClient: HttpClient) =
    get("/cpa/adresseregister/her/{$HER_ID}/encryption") {
        val herId = call.parameters[HER_ID] ?: throw BadRequestException("Mangler $HER_ID")
        try {
            val certificate = httpClient.fetchAREncryptCertificate(herId)
            call.respond(certificate)
        } catch (ex: Exception) {
            log.error("Error while fetching encryption certificate <$herId>", ex)
            call.respondText(
                ex.localizedMessage,
                ContentType.Text.Plain,
                HttpStatusCode.InternalServerError
            )
        }
    }

suspend fun HttpClient.fetchARHerId(herId: String): Long {
    val baseUrl = config().nhn.adresseregisterApiBaseUrl
    return try {
        val response: HttpResponse = this.get("$baseUrl/$herId")
        if (response.status == HttpStatusCode.OK) {
            log.info("Data mottatt: ${response.bodyAsText()}")
        } else {
            log.warn("Feil ved oppslag: ${response.status}")
        }
        val communicationParty = response.body<CommunicationParty>()

        communicationParty.herId
    } catch (e: Exception) {
        log.error("Kunne ikke koble til $baseUrl: ${e.localizedMessage}", e)
        e.localizedMessage
        throw e
    }
}

suspend fun HttpClient.fetchCommunicationParty(herId: String? = null): CommunicationParty {
    val baseUrl = config().nhn.adresseregisterApiBaseUrl
    return try {
        val response: HttpResponse = this.get("$baseUrl/$herId")
        if (response.status == HttpStatusCode.OK) {
            log.info("Data mottatt: ${response.bodyAsText()}")
        } else {
            log.warn("Feil ved oppslag: ${response.status}")
        }

        val communicationParty = response.body<CommunicationParty>()
        communicationParty
    } catch (e: Exception) {
        log.error("Kunne ikke koble til $baseUrl: ${e.localizedMessage}", e)
        e.localizedMessage
        throw e
    }
}

suspend fun HttpClient.fetchARSignCertificate(herId: String): Certificate {
    val baseUrl = config().nhn.adresseregisterApiCertificateBaseUrl
    return try {
        val response: HttpResponse = this.get("$baseUrl/$herId/signing")
        if (response.status == HttpStatusCode.OK) {
            log.info("Data mottatt: ${response.bodyAsText()}")
        } else {
            log.warn("Feil ved oppslag: Connect to $$baseUrl --> ${response.status} ")
        }

        response.body<Certificate>()
    } catch (e: Exception) {
        log.error("Kunne ikke koble til $baseUrl: ${e.localizedMessage}", e)
        e.localizedMessage
        throw e
    }
}

suspend fun HttpClient.fetchAREncryptCertificate(herId: String): Certificate {
    val baseUrl = config().nhn.adresseregisterApiCertificateBaseUrl

    return try {
        val response: HttpResponse = this.get("$baseUrl/$herId/encryption")
        if (response.status == HttpStatusCode.OK) {
            log.info("Data mottatt: ${response.bodyAsText()}")
        } else {
            log.warn("Feil ved oppslag: ${response.status}")
        }

        response.body<Certificate>()
    } catch (e: Exception) {
        log.error("Kunne ikke koble til $baseUrl: ${e.localizedMessage}", e)
        e.localizedMessage
        throw e
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
            cpaRepository.findTimestampCpaLatestUpdated()
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
private const val REQUEST_ID = "requestId"
private const val HER_ID = "herId"
private const val PARTNER_ID = "partnerId"
private const val ROLE = "role"
private const val SERVICE = "service"
private const val ACTION = "action"
