package no.nav.emottak.payload.helseid

import java.security.cert.X509Certificate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import no.nav.emottak.crypto.KeyStoreManager
import no.nav.emottak.payload.crypto.payloadSigneringConfig
import no.nav.emottak.payload.defaultHttpClient
import no.nav.emottak.payload.helseid.util.msgHeadNamespaceContext
import no.nav.emottak.payload.ocspstatus.OcspStatusService
import org.slf4j.LoggerFactory
import org.w3c.dom.Document

class NinResolver(
    private val tokenValidator: HelseIdTokenValidator = HelseIdTokenValidator(),
    private val ocspStatusService: OcspStatusService = OcspStatusService(
        defaultHttpClient().invoke(),
        KeyStoreManager(payloadSigneringConfig())
    )
) {
    private val log = LoggerFactory.getLogger(NinResolver::class.java)

    suspend fun resolve(document: Document, certificate: X509Certificate): String? {
        val token = tokenValidator.getHelseIdTokenFromDocument(document)

        val nin = token?.let {
            runCatching {
                tokenValidator.getValidatedNin(it, parseDateOrThrow(extractGeneratedDate(document)))
            }.onFailure { log.error("HelseID validation failed", it) }.getOrNull()
        }

        return nin ?: ocspStatusService.getOCSPStatus(certificate).fnr
    }

    private fun extractGeneratedDate(document: Document): String? {
        val ns = msgHeadNamespaceContext.getNamespaceURI("mh") ?: return null
        return document.getElementsByTagNameNS(ns, "GenDate").item(0)?.textContent
    }

    private fun parseDateOrThrow(date: String?): ZonedDateTime {
        requireNotNull(date) { "GenDate missing or empty in document" }
        return ZonedDateTime.parse(date, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}
