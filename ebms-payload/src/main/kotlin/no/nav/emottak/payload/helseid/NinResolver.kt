package no.nav.emottak.payload.helseid

import no.nav.emottak.crypto.KeyStoreManager
import no.nav.emottak.payload.configuration.config
import no.nav.emottak.payload.defaultHttpClient
import no.nav.emottak.payload.helseid.util.msgHeadNamespaceContext
import no.nav.emottak.payload.ocspstatus.OcspStatusService
import org.w3c.dom.Document
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class NinResolver(
    private val tokenValidator: HelseIdTokenValidator = HelseIdTokenValidator(),
    private val ocspStatusService: OcspStatusService = OcspStatusService(
        defaultHttpClient().invoke(),
        KeyStoreManager(*config().signering.map { it.resolveKeyStoreConfiguration() }.toTypedArray())
    )
) {
    fun resolve(token: String, messageGenerationDate: Instant): String? {
        return tokenValidator.getValidatedNin(token, messageGenerationDate)
    }

    suspend fun resolve(document: Document, certificate: X509Certificate): String? {
        val token = tokenValidator.getHelseIdTokenFromDocument(document)

        val nin = token?.let {
            tokenValidator.getValidatedNin(it, parseDateOrThrow(extractGeneratedDate(document)))
        }

        return nin ?: ocspStatusService.getOCSPStatus(certificate).fnr
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
