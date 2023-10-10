/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package no.nav.emottak.ebms

import com.sun.xml.messaging.saaj.soap.ver1_1.SOAPMessageFactory1_1Impl
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jakarta.xml.soap.SOAPConstants
import jakarta.xml.soap.SOAPFault
import jakarta.xml.soap.SOAPMessage
import no.nav.emottak.ebms.model.*
import no.nav.emottak.ebms.processing.EbmsMessageProcessor
import no.nav.emottak.ebms.validation.MimeValidationException
import no.nav.emottak.ebms.validation.asParseAsSoapFault
import no.nav.emottak.ebms.validation.validateMime
import no.nav.emottak.ebms.validation.validateMimeAttachment
import no.nav.emottak.ebms.validation.validateMimeSoapEnvelope
import no.nav.emottak.ebms.xml.xmlMarshaller
import org.xmlsoap.schemas.soap.envelope.Envelope
import java.time.LocalDateTime
import javax.xml.namespace.QName


fun main() {
    //val database = Database(mapHikariConfig(DatabaseConfig()))
    //database.migrate()
    embeddedServer(Netty, port = 8080, module = Application::myApplicationModule).start(wait = true)
}

fun PartData.payload() : ByteArray {
    if (this is PartData.FormItem) return java.util.Base64.getMimeDecoder().decode(this.value)
    if (this is PartData.FileItem) {
        val bytes = this.streamProvider.invoke().readAllBytes()
        return java.util.Base64.getMimeDecoder().decode(bytes)
    }
    return byteArrayOf()

}

fun Application.myApplicationModule() {
    routing {
        get("/") {
            call.application.environment.log.info("TESTEST")
            call.respondText("Hello, world!")
        }
        post("/ebms") {

            // KRAV 5.5.2.1 validate MIME
            try {
                call.request.headers.validateMime()
            } catch(it:MimeValidationException) {
                call.respond(HttpStatusCode.InternalServerError,it.asParseAsSoapFault())
                return@post

            }
            val allParts = call.receiveMultipart().readAllParts()
            val dokument = allParts.find {
                it.contentType?.contentType + "/" + it.contentType?.contentSubtype == "text/xml" && it.contentDisposition == null
            }
            val attachments = allParts.filter { it.contentDisposition?.disposition == ContentDisposition.Attachment.disposition }
            try {
                 dokument?.validateMimeSoapEnvelope() ?: throw MimeValidationException("Unable to find soap envelope multipart")
                attachments.forEach {
                        it.validateMimeAttachment()
                 }

            }catch (ex: MimeValidationException) {
                call.respond(HttpStatusCode.InternalServerError,ex.asParseAsSoapFault())
                return@post
            }

            val dokumentWithAttachment = EbMSDocument(
                "",
                dokument!!.payload(),
                attachments.map {
                    EbMSAttachment(
                        it.payload(),
                        it.contentType!!.contentType,
                        it.headers["Content-Id"]!!
                    )
                })
            val processor = EbmsMessageProcessor(dokumentWithAttachment, dokumentWithAttachment.buildEbmMessage())
            processor.runAll()

            //call payload processor
            println(dokumentWithAttachment)

            call.respondText("Hello")
        }

        post("/ebxmlMessage") {
            val envelope = xmlMarshaller.unmarshal(call.receiveText(), Envelope::class.java)
            val ebMSMessage = EbMSMessage(envelope.header(), envelope.ackRequested(), emptyList(), LocalDateTime.now())
            //TODO ordentlig ebmsdocument
            EbmsMessageProcessor(EbMSDocument("", byteArrayOf(), emptyList()), ebMSMessage).runAll()
        }

        post("/ebmsTest") {
            val allParts = call.receiveMultipart().readAllParts()
            val dokument = allParts.find {
                it.contentType?.toString() == "text/xml" && it.contentDisposition == null
            }
            val envelope =
                xmlMarshaller.unmarshal(String((dokument as PartData.FormItem).payload()), Envelope::class.java)
           //val attachments = allParts
           //    .filter { it.contentDisposition == ContentDisposition.Attachment }
           //    .filter { it.headers.get("Content-Id")?.contains(attachmentId, true) ?: false }
           //    .map { (it as PartData.FormItem).payload() }
           //    .first()
           //println(
           //    String(attachments)
           //)
            call.respondText("Hello2")
        }
    }
}
