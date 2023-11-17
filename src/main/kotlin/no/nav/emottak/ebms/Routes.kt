package no.nav.emottak.ebms

import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.EbMSPayloadMessage
import no.nav.emottak.ebms.model.buildEbmMessage
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.ebms.validation.MimeValidationException
import no.nav.emottak.ebms.validation.asParseAsSoapFault
import no.nav.emottak.ebms.validation.validateMime
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.asErrorList
import no.nav.emottak.util.retrieveLoggableHeaderPairs
import no.nav.emottak.util.marker


fun Route.postEbms(validator: DokumentValidator, processingService: ProcessingService, cpaRepoClient: CpaRepoClient): Route =
    post("/ebms") {
        // KRAV 5.5.2.1 validate MIME
        val debug:Boolean = call.request.header("debug")?.isNotBlank()?: false
        val ebMSDocument: EbMSDocument
        try {
            call.request.validateMime()
            ebMSDocument = call.receiveEbmsDokument()
            // TODO gjøre dette bedre
            log.info(ebMSDocument.messageHeader().marker(call.request.headers.retrieveLoggableHeaderPairs()), "Melding mottatt")
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

        val validationResult = validator.validate(ebMSDocument)
        if (!validationResult.valid()) {
            ebMSDocument
                .createFail(validationResult.error!!.asErrorList())
                .toEbmsDokument()
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
        } catch (ex: EbmsException) {
            logger().error(message.messageHeader.marker(), "Processing failed: ${ex.message}", ex)
            ebMSDocument
                .createFail(ex.errorCode.createEbxmlError())
                .toEbmsDokument()
                .also {
                    call.respondEbmsDokument(it)
                }
            return@post
        }
        catch(ex: ServerResponseException) {
            logger().error(message.messageHeader.marker(), "Processing failed: ${ex.message}", ex)
            ebMSDocument
                .createFail(ErrorCode.UNKNOWN.createEbxmlError("Processing failed: ${ex.message}"))
                .toEbmsDokument()
                //  .signer(cpa.signatureDetails) //@TODO hva skjer hvis vi klarer ikke å hente signature details ?
                .also {
                    call.respondEbmsDokument(it)
                    return@post
                }
        } catch(ex: ClientRequestException) {
            logger().error(message.messageHeader.marker(), "Processing failed: ${ex.message}", ex)
            ebMSDocument
                .createFail(ErrorCode.OTHER_XML.createEbxmlError("Processing failed: ${ex.message}"))
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
