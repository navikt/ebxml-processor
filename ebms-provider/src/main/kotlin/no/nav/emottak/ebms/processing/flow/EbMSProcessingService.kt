package no.nav.emottak.ebms.processing.flow

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.nav.emottak.ebms.model.EbMSAttachment
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.EbMSMessage
import no.nav.emottak.ebms.model.buildEbmMessage
import no.nav.emottak.ebms.payload
import no.nav.emottak.ebms.processing.*
import no.nav.emottak.ebms.validation.MimeValidationException
import no.nav.emottak.ebms.validation.asParseAsSoapFault
import no.nav.emottak.ebms.validation.validateMimeAttachment
import no.nav.emottak.ebms.validation.validateMimeSoapEnvelope
import java.util.UUID

class EbMSProcessingService(val applicationCall: ApplicationCall) {

    val korrelasjonsId: String = UUID.randomUUID().toString();

    private fun preProcessorCollection() = listOf(
            MimeValidationProcessor(applicationCall.request.headers, korrelasjonsId),
            //TaskProcessor(this::parseEbmsMessage, korrelasjonsId),
    )

    private fun ebxmlProcessCollection(ebMSDocument: EbMSDocument, ebMSMessage: EbMSMessage) = listOf(
            CPAValidationProcessor(ebMSMessage),
            SertifikatsjekkProcessor(ebMSMessage),
            SignatursjekkProcessor(ebMSDocument, ebMSMessage),
            AckRequestedProcessor(ebMSMessage),
            lastProcess(ebMSMessage)
        )

    //private fun rest() = listOf(
    //    AckProcessor(ebMSMessage),
    //    PayloadProcessor(ebMSMessage),
    //    ErrorListProcessor(ebMSMessage)
    //)

    fun run() {
        suspend {
            try {
                preProcessorCollection()
                    .forEach { p -> p.processWithEvents() }

                parseEbmsDocument()
                    .let {  ebxmlProcessCollection(it, it.buildEbmMessage()) }
                    .forEach { p -> p.processWithEvents() }

            } catch (e: EbMSMessageProcessor.EbxmlProcessException) {
                // impl errorhandling
            } catch (it: MimeValidationException) { // TODO generify
                applicationCall
                    .respond(HttpStatusCode.InternalServerError,it.asParseAsSoapFault())
            }
        }
    }

    fun lastProcess(ebMSMessage: EbMSMessage): Processor {
        return when(ebMSMessage.type()) {
            EbMSMessage.Type.ACK    -> TaskProcessor(this::handleAck, "")
            EbMSMessage.Type.ERROR  -> TaskProcessor(this::handleError, "")
            EbMSMessage.Type.PAYLOAD -> PayloadProcessor(ebMSMessage)
        }
    }

    fun handleAck() {

    }
    fun handleError() {

    }

    suspend fun parseEbmsDocument(): EbMSDocument {
        val dokument = applicationCall.receiveMultipart().readAllParts().find {
            it.contentType?.contentType + "/" + it.contentType?.contentSubtype == "text/xml" && it.contentDisposition == null
        }!!

        val allParts = applicationCall.receiveMultipart().readAllParts()

        val attachments = allParts.filter { it.contentDisposition?.disposition == ContentDisposition.Attachment.disposition }
        try {
            dokument?.validateMimeSoapEnvelope() ?: throw MimeValidationException("Unable to find soap envelope multipart")
            attachments.forEach {
                it.validateMimeAttachment()
            }
        }catch (ex: MimeValidationException) {
            applicationCall.respond(HttpStatusCode.InternalServerError,ex.asParseAsSoapFault())
        }
        return EbMSDocument(
            "",
            dokument!!.payload(),
            attachments.map {
                EbMSAttachment(
                    it.payload(),
                    it.contentType!!.contentType,
                    it.headers["Content-Id"]!!
                )
            })
    }
}