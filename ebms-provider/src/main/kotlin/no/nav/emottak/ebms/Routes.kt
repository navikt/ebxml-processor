package no.nav.emottak.ebms

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.serialization.Serializable
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.ebms.configuration.config
import no.nav.emottak.ebms.messaging.EbmsSignalProducer
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.ebms.validation.MimeValidationException
import no.nav.emottak.ebms.validation.parseAsSoapFault
import no.nav.emottak.ebms.validation.validateMime
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.EbMSDocument
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.PayloadProcessing
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.message.model.toEbmsMessageDetails
import no.nav.emottak.message.xml.asByteArray
import no.nav.emottak.util.marker
import no.nav.emottak.util.retrieveLoggableHeaderPairs
import java.sql.SQLException
import java.util.UUID

data class PackageRequest(
    val payload: EbmsMessage
)

fun Route.unpackageEbxml(
    validator: DokumentValidator,
    processingService: ProcessingService
): Route = post("/ebxml/unpack") {
    val debug: Boolean = call.request.header("debug")?.isNotBlank() ?: false
    val ebMSDocument: EbMSDocument
    val loggableHeaders = call.request.headers.retrieveLoggableHeaderPairs()
    try {
        call.request.validateMime()
        ebMSDocument = call.receiveEbmsDokument()
        log.info(ebMSDocument.messageHeader().marker(loggableHeaders), "Melding mottatt")
    } catch (ex: MimeValidationException) {
        log.error(
            call.request.headers.marker(),
            "Mime validation has failed: ${ex.message} Message-Id ${call.request.header(SMTPHeaders.MESSAGE_ID)}",
            ex
        )
        call.respond(HttpStatusCode.InternalServerError, ex.parseAsSoapFault())
        return@post
    } catch (ex: Exception) {
        log.error(
            call.request.headers.marker(),
            "Unable to transform request into EbmsDokument: ${ex.message} " +
                "Message-Id ${call.request.header(SMTPHeaders.MESSAGE_ID)}",
            ex
        )
        // @TODO done only for demo fiks!
        call.respond(
            HttpStatusCode.InternalServerError,
            ex.parseAsSoapFault()
        )
        return@post
    }

    val ebmsMessage = ebMSDocument.transform() as PayloadMessage
    var signingCertificate: SignatureDetails? = null
    try {
        validator.validateIn(ebmsMessage)
            .let { validationResult ->
                processingService.processSyncIn(ebmsMessage, validationResult.payloadProcessing)
            }.let {
                call.respond(it.first)
            }
    } catch (ebmsException: EbmsException) {
        log.error(ebmsMessage.marker(), ebmsException.message, ebmsException)
        ebmsMessage.createFail(ebmsException.feil).toEbmsDokument().also {
            signingCertificate?.let { signatureDetails ->
                it.signer(signatureDetails)
            }
            call.respondEbmsDokument(it)
            return@post
        }
    } catch (ex: Exception) {
        log.error(ebmsMessage.marker(), "Unknown error during message processing: ${ex.message}", ex)
        call.respond(
            HttpStatusCode.InternalServerError,
            ex.parseAsSoapFault()
        )
    }
}

fun Route.packageEbxml(
    validator: DokumentValidator,
    processingService: ProcessingService
): Route = post("/ebxml/pack") {
    val payloadMessage = call.receive(PayloadMessage::class)
    var signingCertificate: SignatureDetails? = null
    try {
        validator.validateOut(payloadMessage).let {
            signingCertificate = it.payloadProcessing?.signingCertificate
            Pair<PayloadMessage, PayloadProcessing?>(payloadMessage, it.payloadProcessing)
        }.let { messageProcessing ->
            val processedMessage =
                processingService.proccessSyncOut(messageProcessing.first, messageProcessing.second)
            Pair<PayloadMessage, PayloadProcessing?>(processedMessage, messageProcessing.second)
        }
            .let {
                call.respondEbmsDokument(
                    it.first.toEbmsDokument().also { ebmsDocument ->
                        ebmsDocument.signer(it.second!!.signingCertificate)
                    }
                )
                log.info(it.first.marker(), "Melding ferdig behandlet og svar returnert")
                return@post
            }
    } catch (ebmsException: EbmsException) {
        log.error(payloadMessage.marker(), ebmsException.message, ebmsException)
        payloadMessage.createFail(ebmsException.feil).toEbmsDokument().also {
            signingCertificate?.let { signatureDetails ->
                it.signer(signatureDetails)
            }
            call.respondEbmsDokument(it)
            return@post
        }
    } catch (ex: Exception) {
        log.error(payloadMessage.marker(), "Unknown error during message processing: ${ex.message}", ex)
        call.respond(
            HttpStatusCode.InternalServerError,
            ex.parseAsSoapFault()
        )
    }
}

fun Route.postEbmsSync(
    validator: DokumentValidator,
    processingService: ProcessingService,
    sendInService: SendInService,
    ebmsMessageDetailsRepository: EbmsMessageDetailsRepository
): Route = post("/ebms/sync") {
    log.info("Receiving synchronous request")

    val debug: Boolean = call.request.header("debug")?.isNotBlank() ?: false
    val ebMSDocument: EbMSDocument
    val loggableHeaders = call.request.headers.retrieveLoggableHeaderPairs()
    try {
        call.request.validateMime()
        ebMSDocument = call.receiveEbmsDokument()
        log.info(ebMSDocument.messageHeader().marker(loggableHeaders), "Melding mottatt")
    } catch (ex: MimeValidationException) {
        logger().error(
            call.request.headers.marker(),
            "Mime validation has failed: ${ex.message} Message-Id ${call.request.header(SMTPHeaders.MESSAGE_ID)}",
            ex
        )
        call.respond(HttpStatusCode.InternalServerError, ex.parseAsSoapFault())
        return@post
    } catch (ex: Exception) {
        logger().error(
            call.request.headers.marker(),
            "Unable to transform request into EbmsDokument: ${ex.message} " +
                "Message-Id ${call.request.header(SMTPHeaders.MESSAGE_ID)}",
            ex
        )
        // @TODO done only for demo fiks!
        call.respond(
            HttpStatusCode.InternalServerError,
            ex.parseAsSoapFault()
        )
        return@post
    }

    saveEbmsMessageDetails(ebMSDocument, loggableHeaders, ebmsMessageDetailsRepository)

    val ebmsMessage = ebMSDocument.transform() as PayloadMessage
    var signingCertificate: SignatureDetails? = null
    try {
        validator.validateIn(ebmsMessage)
            .let { validationResult ->
                processingService.processSyncIn(ebmsMessage, validationResult.payloadProcessing)
            }.let { processedMessage ->
                when (processedMessage.second) {
                    Direction.IN -> {
                        sendInService.sendIn(processedMessage.first).let {
                            PayloadMessage(
                                requestId = UUID.randomUUID().toString(),
                                messageId = UUID.randomUUID().toString(),
                                conversationId = it.conversationId,
                                cpaId = ebmsMessage.cpaId,
                                addressing = it.addressing,
                                payload = Payload(it.payload, ContentType.Application.Xml.toString()),
                                refToMessageId = it.messageId
                            )
                        }
                    }

                    else -> processedMessage.first
                }
            }.let { payloadMessage ->
                validator.validateOut(payloadMessage).let {
                    signingCertificate = it.payloadProcessing?.signingCertificate
                    Pair<PayloadMessage, PayloadProcessing?>(payloadMessage, it.payloadProcessing)
                }
            }.let { messageProcessing ->
                val processedMessage =
                    processingService.proccessSyncOut(messageProcessing.first, messageProcessing.second)
                Pair<PayloadMessage, PayloadProcessing?>(processedMessage, messageProcessing.second)
            }.let {
                call.respondEbmsDokument(
                    it.first.toEbmsDokument().also { ebmsDocument ->
                        ebmsDocument.signer(it.second!!.signingCertificate)
                    }
                )
                log.info(it.first.marker(), "Melding ferdig behandlet og svar returnert")
                return@post
            }
    } catch (ebmsException: EbmsException) {
        log.error(ebmsMessage.marker(), ebmsException.message, ebmsException)
        ebmsMessage.createFail(ebmsException.feil).toEbmsDokument().also {
            signingCertificate?.let { signatureDetails ->
                it.signer(signatureDetails)
            }
            log.info(ebmsMessage.marker(), "Created MessageError response")
            call.respondEbmsDokument(it)
            return@post
        }
    } catch (ex: Exception) {
        log.error(ebmsMessage.marker(), "Unknown error during message processing: ${ex.message}", ex)
        call.respond(
            HttpStatusCode.InternalServerError,
            ex.parseAsSoapFault()
        )
    }
}

fun Route.postEbmsAsync(
    validator: DokumentValidator,
    processingService: ProcessingService,
    ebmsMessageDetailsRepository: EbmsMessageDetailsRepository,
    ebmsSignalProducer: EbmsSignalProducer
): Route =
    post("/ebms/async") {
        // KRAV 5.5.2.1 validate MIME
        val debug: Boolean = call.request.header("debug")?.isNotBlank() ?: false
        val ebMSDocument: EbMSDocument
        try {
            call.request.validateMime()
            ebMSDocument = call.receiveEbmsDokument()
        } catch (ex: MimeValidationException) {
            logger().error(
                "Mime validation has failed: ${ex.message} Message-Id ${call.request.header(SMTPHeaders.MESSAGE_ID)}",
                ex
            )
            call.respond(HttpStatusCode.InternalServerError, ex.parseAsSoapFault())
            return@post
        } catch (ex: Exception) {
            logger().error(
                "Unable to transform request into EbmsDokument: ${ex.message} Message-Id ${
                call.request.header(
                    SMTPHeaders.MESSAGE_ID
                )
                }",
                ex
            )
            // @TODO done only for demo fiks!
            call.respond(
                HttpStatusCode.InternalServerError,
                ex.parseAsSoapFault("Unable to transform request into EbmsDokument")
            )
            return@post
        }

        // TODO gjøre dette bedre
        val loggableHeaders = call.request.headers.retrieveLoggableHeaderPairs()
        val ebmsMessage = ebMSDocument.transform()
        log.info(ebMSDocument.messageHeader().marker(loggableHeaders), "Melding mottatt")

        saveEbmsMessageDetails(ebMSDocument, loggableHeaders, ebmsMessageDetailsRepository)

        try {
            validator
                .validateIn(ebmsMessage)
                .also {
                    processingService.processAsync(ebmsMessage, it.payloadProcessing)
                }
            if (ebmsMessage !is PayloadMessage) {
                log.info(ebMSDocument.messageHeader().marker(), "Successfully processed Signal Message")
                call.respondText("Processed")
                return@post
            }
            log.info(ebMSDocument.messageHeader().marker(), "Payload Processed, Generating Acknowledgement...")
            ebmsMessage.createAcknowledgment().toEbmsDokument().also {
                log.debug("Sending acknowledgement to Kafka")
                sendToKafka(it, loggableHeaders, ebmsSignalProducer)
            }.also {
                log.debug("Saving acknowledgement to database")
                saveEbmsMessageDetails(it, loggableHeaders, ebmsMessageDetailsRepository)
            }.also {
                call.respondEbmsDokument(it)
                return@post
            }
        } catch (ex: EbmsException) {
            ebmsMessage.createFail(ex.feil).toEbmsDokument().also {
                log.info(ebmsMessage.marker(), "Created MessageError response")
            }.also {
                log.debug("Sending fail message to Kafka")
                sendToKafka(it, loggableHeaders, ebmsSignalProducer)
            }.also {
                log.debug("Saving fail message to database")
                saveEbmsMessageDetails(it, loggableHeaders, ebmsMessageDetailsRepository)
            }.also {
                call.respondEbmsDokument(it)
                return@post
            }
        } catch (ex: Exception) {
            log.error(ebmsMessage.marker(), "Unknown error during message processing: ${ex.message}", ex)
            call.respond(
                HttpStatusCode.InternalServerError,
                ex.parseAsSoapFault()
            )
            return@post
        }
    }

fun Routing.registerHealthEndpoints(
    collectorRegistry: PrometheusMeterRegistry
) {
    get("/internal/health/liveness") {
        call.respondText("I'm alive! :)")
    }
    get("/internal/health/readiness") {
        call.respondText("I'm ready! :)")
    }
    get("/prometheus") {
        call.respond(collectorRegistry.scrape())
    }
}

fun Routing.navCheckStatus() {
    @Serializable
    data class StatusResponse(val status: String)

    get("/internal/status") {
        call.respond(StatusResponse(status = "OK"))
    }
}

suspend fun sendToKafka(
    ebMSDocument: EbMSDocument,
    loggableHeaders: Map<String, String>,
    producer: EbmsSignalProducer
) {
    if (config().kafkaSignalProducer.active) {
        val markers = ebMSDocument.messageHeader().marker(loggableHeaders)
        try {
            log.info(markers, "Sending message to Kafka queue")
            producer.send(ebMSDocument.requestId, ebMSDocument.dokument.asByteArray())
        } catch (e: Exception) {
            log.error(markers, "Exception occurred while sending message to Kafka queue", e)
        }
    }
}

fun saveEbmsMessageDetails(
    ebMSDocument: EbMSDocument,
    loggableHeaders: Map<String, String>,
    repository: EbmsMessageDetailsRepository
) {
    val ebmsMessageDetails = ebMSDocument.transform().toEbmsMessageDetails()
    val markers = ebMSDocument.messageHeader().marker(loggableHeaders)
    try {
        repository.saveEbmsMessageDetails(ebmsMessageDetails).also {
            if (it == null) {
                log.info(markers, "Message details has not been saved to database")
            } else {
                log.info(markers, "Message details saved to database")
            }
        }
    } catch (ex: SQLException) {
        val hint = repository.handleSQLException(ex)
        log.error(markers, "SQL exception ${ex.sqlState} occurred while saving message details to database: $hint", ex)
    } catch (ex: Exception) {
        log.error(markers, "Error occurred while saving message details to database", ex)
    }
}
