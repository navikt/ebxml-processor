package no.nav.emottak.ebms.processing.flow

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.emottak.ebms.model.*
import no.nav.emottak.ebms.payload
import no.nav.emottak.ebms.processing.*
import no.nav.emottak.ebms.validation.*
import no.nav.emottak.ebms.xml.getDocumentBuilder
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlin.coroutines.coroutineContext


fun Routing.postEbmsMessageEndpoint(): Route {
    return post("/ebmsMessage") {
        EbMSProcessingService(call.request)
    }
}

class EbMSProcessingService(val appRequest: ApplicationRequest) {

    val korrelasjonsId: String = UUID.randomUUID().toString();
    private fun preProcessorCollection() =
        TaskFactory(korrelasjonsId)
            .addTask("Validate MIME headers") { appRequest.headers.validateMimeHeaders() }
            .addTask("Validate Content-Type") { appRequest.contentType().validateContentType() }
            .addTask("Validate MIME SOAP envelope") { suspend { appRequest.call.receiveMultipart().readAllParts() } }
            .tasks
    private fun ebxmlProcessCollection(ebMSDocument: EbMSDocument, ebMSMessage: EbMSBaseMessage) = listOf(
        CPAValidationProcessor(ebMSMessage),
        SertifikatsjekkProcessor(ebMSMessage),
        SignatursjekkProcessor(ebMSDocument, ebMSMessage),
        messageTypeProcessor(ebMSMessage)
    )

    suspend fun test(call: ApplicationCall) {
        when (appRequest.contentType()) {
            ContentType.parse("multipart/related") -> {
                val allParts =  appRequest.call.receiveMultipart().readAllParts()
                try {
                    val dokument = allParts.find {
                        it.contentType?.withoutParameters() == ContentType.parse("text/xml") && it.contentDisposition == null
                    }.also { it?.validateMimeSoapEnvelope() ?: throw MimeValidationException("Unable to find soap envelope multipart") }!!.payload()
                    val attachments =
                        allParts.filter { it.contentDisposition?.disposition == ContentDisposition.Attachment.disposition }
                    attachments.forEach {
                        it.validateMimeAttachment()
                    }
                    EbMSDocument(
                        "",
                        getDocumentBuilder().parse(ByteArrayInputStream(dokument)),
                        attachments.map {
                            EbMSAttachment(
                                it.payload(),
                                it.contentType!!.contentType,
                                it.headers["Content-Id"]!!
                            )
                        })
                } catch (ex: MimeValidationException) {
                    call.respond(HttpStatusCode.InternalServerError, ex.asParseAsSoapFault())
                }
            }
            ContentType.parse("text/xml") -> {
                val dokument = call.receiveStream().readAllBytes()
                EbMSDocument(
                    "",
                    getDocumentBuilder().parse(ByteArrayInputStream(dokument)),
                    emptyList()
                )
            }
            else -> {
                //call.respond(HttpStatusCode.BadRequest, "Ukjent request body med Content-Type $contentType")
                //return
            }
        }
    }



    fun messageTypeProcessor(ebMSMessage: EbMSBaseMessage): Processor {
        return when(ebMSMessage) {
            is EbMSAckMessage -> TaskProcessor("Håndterer ACK message",korrelasjonsId,this::handleAck)
            is EbMSErrorMessage -> TaskProcessor("Håndterer Error Message", korrelasjonsId, this::handleError)
            is EbMSPayloadMessage -> PayloadProcessor(ebMSMessage)
            else -> { throw RuntimeException("Ukjent meldingstype") }
        }
    }
    fun handleAck() {}
    fun handleError() {}

    fun run() {
        suspend {
            try {
                preProcessorCollection()
                    .forEach { p -> p.processWithEvents() }

                //parseEbmsDocument()
                //    .let {  ebxmlProcessCollection(it, it.buildEbmMessage()) }
                //    .forEach { p -> p.processWithEvents() }

            } catch (e: EbMSErrorUtil.EbxmlProcessException) {
                // impl errorhandling
            } catch (it: MimeValidationException) { // TODO generify
                //applicationCall.respond(HttpStatusCode.InternalServerError,it.asParseAsSoapFault())
            }
        }
    }





    //suspend fun parseEbmsDocument(): EbMSDocument {
    //    val dokument = applicationCall.receiveMultipart().readAllParts().find {
    //        it.contentType?.contentType + "/" + it.contentType?.contentSubtype == "text/xml" && it.contentDisposition == null
    //    }!!
//
    //    val allParts = applicationCall.receiveMultipart().readAllParts()
//
    //    val attachments = allParts.filter { it.contentDisposition?.disposition == ContentDisposition.Attachment.disposition }
    //    try {
    //        dokument?.validateMimeSoapEnvelope() ?: throw MimeValidationException("Unable to find soap envelope multipart")
    //        attachments.forEach {
    //            it.validateMimeAttachment()
    //        }
    //    }catch (ex: MimeValidationException) {
    //        applicationCall.respond(HttpStatusCode.InternalServerError,ex.asParseAsSoapFault())
    //    }
    //    return EbMSDocument(
    //        "",
    //        dokument!!.payload(),
    //        attachments.map {
    //            EbMSAttachment(
    //                it.payload(),
    //                it.contentType!!.contentType,
    //                it.headers["Content-Id"]!!
    //            )
    //        })
    //}
}

class Validator() {

}