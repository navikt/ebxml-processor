package no.nav.emottak.ebms

import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.emottak.ebms.model.Cpa
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.EbMSErrorUtil
import no.nav.emottak.ebms.model.EbMSPayloadMessage
import no.nav.emottak.ebms.model.buildEbmMessage
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.ebms.validation.MimeValidationException
import no.nav.emottak.ebms.validation.asParseAsSoapFault
import no.nav.emottak.ebms.validation.validateMime
import no.nav.emottak.util.marker


fun Route.postEbms(validator: DokumentValidator, processingService: ProcessingService, cpaRepoClient: CpaRepoClient): Route =
    post("/ebms") {
        // KRAV 5.5.2.1 validate MIME
        val debug:Boolean = call.request.header("debug")?.isNotBlank()?: false
        try {
            call.request.validateMime()
        } catch (ex: MimeValidationException) {
            logger().error("Mime validation has failed: ${ex.message}", ex)
            call.respond(HttpStatusCode.InternalServerError, ex.asParseAsSoapFault())
            return@post

        }

        val ebMSDocument: EbMSDocument
        try {
            ebMSDocument = call.receiveEbmsDokument()
        } catch (ex: MimeValidationException) {
            logger().error("Mime validation has failed: ${ex.message}", ex)
            call.respond(HttpStatusCode.InternalServerError, ex.asParseAsSoapFault())
            return@post
        } catch (ex: Exception) {
            logger().error("Unable to transform request into EbmsDokument: ${ex.message}", ex)

            //@TODO done only for demo fiks!
            call.respond(HttpStatusCode.InternalServerError, MimeValidationException("Unable to transform request into EbmsDokument: ${ex.message}",ex).asParseAsSoapFault())
            return@post
        }
        var cpa: Cpa? = runCatching {
            Cpa(cpaRepoClient.getPublicSigningDetails(ebMSDocument.messageHeader()))
        }.getOrNull()
            //@TODO Hva skall vi gjøre hvis vi henter ikke CPA informasjon ? Vi kan ikke signere feilmeldingene

        try {

            validator.validate(ebMSDocument,cpa?.signatureDetails)
        } catch (ex: MimeValidationException) {
            logger().error(ebMSDocument.messageHeader().marker(), "Mime validation has failed: ${ex.message}", ex)
            call.respond(HttpStatusCode.InternalServerError, ex.asParseAsSoapFault())
            return@post
        } catch (ex2: Exception) {
            logger().error(ebMSDocument.messageHeader().marker(), "Validation failed: ${ex2.message}", ex2)
            ebMSDocument
                .createFail(EbMSErrorUtil.createError(EbMSErrorUtil.Code.OTHER_XML.name, "Validation failed: ${ex2.message}"))
                .toEbmsDokument()
              //  .signer(cpa.signatureDetails) //@TODO hva skjer hvis vi klarer ikke å hente signature details ?
                .also {
                    call.respondEbmsDokument(it)
                    return@post
                }
        }

        val message = ebMSDocument.buildEbmMessage()
        try {
            if (!debug) {
                processingService.process(message)
            }
        } catch(ex: ServerResponseException) {
            logger().error(message.messageHeader.marker(), "Processing failed: ${ex.message}", ex)
            ebMSDocument
                .createFail(EbMSErrorUtil.createError(EbMSErrorUtil.Code.UNKNOWN.name, "Processing failed: ${ex.message}"))
                .toEbmsDokument()
                //  .signer(cpa.signatureDetails) //@TODO hva skjer hvis vi klarer ikke å hente signature details ?
                .also {
                    call.respondEbmsDokument(it)
                    return@post
                }
        } catch(ex: ClientRequestException) {
            logger().error(message.messageHeader.marker(), "Processing failed: ${ex.message}", ex)
            ebMSDocument
                .createFail(EbMSErrorUtil.createError(EbMSErrorUtil.Code.OTHER_XML.name, "Processing failed: ${ex.message}"))
                .toEbmsDokument()
                //  .signer(cpa.signatureDetails) //@TODO hva skjer hvis vi klarer ikke å hente signature details ?
                .also {
                    call.respondEbmsDokument(it)
                    return@post
                }
        } catch (ex: Exception) {
            logger().error(message.messageHeader.marker(), "Processing failed: ${ex.message}", ex)
            call.respond(HttpStatusCode.InternalServerError, "Feil ved prosessering av melding")
            return@post
        }

        //call payload processor
        println(ebMSDocument)
        if (message is EbMSPayloadMessage) {
            message.createAcknowledgment().toEbmsDokument().also {
                call.respondEbmsDokument(it)
                return@post
            }
        }
        call.respondText("Processed")
    }
