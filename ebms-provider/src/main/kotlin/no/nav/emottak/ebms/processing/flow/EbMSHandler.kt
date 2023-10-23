package no.nav.emottak.ebms.processing.flow

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import no.nav.emottak.Event
import no.nav.emottak.ebms.model.*
import no.nav.emottak.ebms.payload
import no.nav.emottak.ebms.processing.*
import no.nav.emottak.ebms.validation.*
import no.nav.emottak.ebms.xml.getDocumentBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.util.UUID


fun Routing.postEbmsMessageEndpoint(): Route {
    return post("/ebmsMessage") {
        EbMSHandler(call.request).behandle()
    }
}

class EbMSHandler(val appRequest: ApplicationRequest) {

    val korrelasjonsId: String = UUID.randomUUID().toString();
    fun validateCPA(ebmsMessage: EbMSBaseMessage) {
    }

    fun sjekkSertifikat(ebmsMessage: EbMSBaseMessage) {
    }

    fun behandle() {
        try {
            val ebMSDocument = runWithEvents(
                Task("Validate Mime") { appRequest.validateMime() },
                Task("Validate Content Type") { appRequest.contentType().validateContentType() },
                Task("Parse EBMSdoc") { runBlocking { parseEbmsDoc(appRequest.call) } }
            ).first { r -> r is EbMSDocument } as EbMSDocument
            runWithEvents("Sjekk Signatur") { ebMSDocument.sjekkSignature() }

            val ebmsMessage = runWithEvents("Build EBMS message") { ebMSDocument.buildEbmMessage() }
            runWithEvents(
                Task("Validate CPA") { validateCPA(ebmsMessage) },
                Task("Sertifikatsjekk") { sjekkSertifikat(ebmsMessage) },
                Task("Sjekker meldingstype") {
                    when (ebmsMessage) {
                        is EbMSAckMessage -> runWithEvents("Håndterer ACK message") { this::handleAckMessage }
                        is EbMSErrorMessage -> runWithEvents("Håndterer Error Message") { this::handleErrorMessage }
                        is EbMSPayloadMessage -> ProcessingService() // TODO
                        else -> {
                            throw RuntimeException("Ukjent meldingstype")
                        }
                    }
                }
            )
            runBlocking { appRequest.call.respond("OK") }
        } catch (e: EbMSErrorUtil.EbxmlProcessException) {
            runWithEvents("Error Handling") { handleEbmsError(e) }
            runBlocking { appRequest.call.respond("Failed") }
        }
    }

    fun handleEbmsError(e: EbMSErrorUtil.EbxmlProcessException) {
        when(e) {
            is MimeValidationException -> runBlocking { appRequest.call.respond(e.asParseAsSoapFault()) }
        }
        e.printStackTrace()
        // Todo
    }

    data class Task<T>(val description: String, val task: () -> T )

    fun <T> runWithEvents(tasks: List<Task<T>>): List<T> {
        val results = ArrayList<T>()
        tasks.forEach { t -> results.add(
            runWithEvents(t.description, t.task)
        ) }
        return results
    }
    fun <T> runWithEvents(vararg tasks: Task<T> ): List<T> {
        val results = ArrayList<T>()
        tasks.forEach { t -> results.add(
            runWithEvents(t.description, t.task)
        ) }
        return results
    }

    fun <T> runWithEvents(eventDescription: String, task: () -> T): T {
        val log = LoggerFactory.getLogger(task::class.java.simpleName)
        val result: T?

        persisterHendelse(Event(eventDescription, Event.Status.STARTED, eventDescription, korrelasjonsId), log)
        try {
            result = task.invoke()
            persisterHendelse(Event(eventDescription, Event.Status.OK, eventDescription, korrelasjonsId), log)
        } catch (t: Throwable) {
            persisterHendelse(Event(eventDescription, Event.Status.FAILED, eventDescription, korrelasjonsId), log)
            throw t;
        }
        return result
    }

    fun persisterHendelse(event: Event, logger: Logger?): Boolean {
        when (event.eventStatus) {
            Event.Status.STARTED, Event.Status.OK -> logger?.info("$event")
            Event.Status.FAILED -> logger?.error("$event")
        }
        return true; // TODO publiser hendelse
    }

     suspend fun parseEbmsDoc(call: ApplicationCall): EbMSDocument {
        when (appRequest.contentType().withoutParameters()) {
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
                    return EbMSDocument(
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
                return EbMSDocument(
                    "",
                    getDocumentBuilder().parse(ByteArrayInputStream(dokument)),
                    emptyList()
                )
            }
            else -> {
                throw RuntimeException("Ukjent request body med Content-Type: " + appRequest.contentType())
                //call.respond(HttpStatusCode.BadRequest, "Ukjent request body med Content-Type $contentType")
                //return
            }
        }
        throw RuntimeException("Klarte ikke parse EbMSDocument")
    }
    fun handleAckMessage() {}
    fun handleErrorMessage() {}

}