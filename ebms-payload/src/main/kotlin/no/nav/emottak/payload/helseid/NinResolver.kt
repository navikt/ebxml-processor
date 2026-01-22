package no.nav.emottak.payload.helseid

import no.nav.emottak.payload.helseid.util.msgHeadNamespaceContext
import org.w3c.dom.Document
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class NinResolver(
    private val tokenValidator: HelseIdTokenValidator = HelseIdTokenValidator()
) {
    fun resolve(token: String, messageGenerationDate: Instant): String? {
        return tokenValidator.getValidatedNin(token, messageGenerationDate)
    }

    fun resolve(document: Document): String? {
        val token = tokenValidator.getHelseIdTokenFromDocument(document)

        if (token == null) {
            throw RuntimeException("No HelseID token found in document")
        }
        return resolve(token, parseDateOrThrow(extractGeneratedDate(document)))
    }

    private fun extractGeneratedDate(document: Document): String? {
        val ns = msgHeadNamespaceContext.getNamespaceURI("mh") ?: return null
        return document.getElementsByTagNameNS(ns, "GenDate").item(0)?.textContent
    }

    private fun parseDateOrThrow(date: String?): Instant {
        requireNotNull(date) { "GenDate element missing or empty in document" }
        return OffsetDateTime.parse(date, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
    }
}
