package no.nav.emottak

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.content.PartData
import io.ktor.util.CaseInsensitiveMap
import jakarta.mail.internet.MimeUtility
import kotlinx.serialization.json.Json
import no.nav.emottak.smtp.EmailMsg
import no.nav.emottak.smtp.MimeHeaders
import no.nav.emottak.smtp.SMTPHeaders
import no.nav.emottak.smtp.getEnvVar
import no.nav.emottak.smtp.log
import java.time.Instant

val URL_CPA_REPO_BASE = getEnvVar("URL_CPA_REPO", "http://cpa-repo.team-emottak.svc.nais.local")
val URL_CPA_REPO_PUT = "$URL_CPA_REPO_BASE/cpa"
val URL_CPA_REPO_DELETE = "$URL_CPA_REPO_BASE/cpa/delete"
val URL_CPA_REPO_TIMESTAMPS = "$URL_CPA_REPO_BASE/cpa/timestamps"

// val URL_EBMS_PROVIDER_BASE = getEnvVar("URL_EBMS_PROVIDER", "http://ebms-provider.team-emottak.svc.nais.local")
val URL_EBMS_PROVIDER_BASE = getEnvVar("URL_EBMS_PROVIDER", "https://ebms-provider.intern.dev.nav.no")
val URL_EBMS_PROVIDER_POST = "$URL_EBMS_PROVIDER_BASE/ebms"

suspend fun HttpClient.getCPATimestamps() =
    Json.decodeFromString<Map<String, String>>(
        this.get(URL_CPA_REPO_TIMESTAMPS).bodyAsText()
    )

suspend fun HttpClient.getLatestCPATimestamp() =
    Json.decodeFromString<Instant>(
        this.get("$URL_CPA_REPO_BASE/cpa/timestamps/latest").bodyAsText()
    )

suspend fun HttpClient.putCPAinCPARepo(cpaFile: String, lastModified: Instant) =
    this.post(URL_CPA_REPO_PUT) {
        headers {
            header("updated_date", lastModified.toString())
            header("upsert", "true") // Upsert kan nok alltid brukes (?)
        }
        setBody(cpaFile)
    }

suspend fun HttpClient.deleteCPAinCPARepo(cpaId: String) = this.delete("$URL_CPA_REPO_DELETE/$cpaId")

suspend fun HttpClient.postEbmsMessageSinglePart(message: EmailMsg) = this.post(URL_EBMS_PROVIDER_POST) {
    headers(
        message.headers.filterHeader(
            MimeHeaders.MIME_VERSION,
            MimeHeaders.CONTENT_ID,
            MimeHeaders.SOAP_ACTION,
            MimeHeaders.CONTENT_TYPE,
            MimeHeaders.CONTENT_TRANSFER_ENCODING,
            SMTPHeaders.FROM,
            SMTPHeaders.TO,
            SMTPHeaders.MESSAGE_ID,
            SMTPHeaders.DATE,
            SMTPHeaders.X_MAILER
        )
    )
    setBody(
        message.parts.first().bytes
    )
}

suspend fun HttpClient.postEbmsMessageMultiPart(message: EmailMsg): HttpResponse {
    val partData: List<PartData> = message.parts.map { part ->
        PartData.FormItem(
            String(part.bytes),
            {},
            Headers.build(
                part.headers.filterHeader(
                    MimeHeaders.CONTENT_ID,
                    MimeHeaders.CONTENT_TYPE,
                    MimeHeaders.CONTENT_TRANSFER_ENCODING,
                    MimeHeaders.CONTENT_DISPOSITION,
                    MimeHeaders.CONTENT_DESCRIPTION
                )
            )
        )
    }
    val contentType = message.headers[MimeHeaders.CONTENT_TYPE]!!
    val boundary = ContentType.parse(contentType).parameter("boundary")

    return this.post(URL_EBMS_PROVIDER_POST) {
        headers(
            message.headers.filterHeader(
                MimeHeaders.MIME_VERSION,
                MimeHeaders.CONTENT_ID,
                MimeHeaders.SOAP_ACTION,
                MimeHeaders.CONTENT_TYPE,
                MimeHeaders.CONTENT_TRANSFER_ENCODING,
                SMTPHeaders.FROM,
                SMTPHeaders.TO,
                SMTPHeaders.MESSAGE_ID,
                SMTPHeaders.DATE,
                SMTPHeaders.X_MAILER
            )
        )
        setBody(
            MultiPartFormDataContent(
                partData,
                boundary!!,
                ContentType.parse(contentType)
            )
        )
    }
}

fun Map<String, String>.filterHeader(vararg headerNames: String): HeadersBuilder.() -> Unit = {
    val caseInsensitiveMap = CaseInsensitiveMap<String>().apply {
        putAll(this@filterHeader)
    }
    headerNames.map {
        Pair(it, caseInsensitiveMap[it])
    }.forEach {
        if (it.second != null) {
            val headerValue = MimeUtility.unfold(it.second!!.replace("\t", " "))
            append(it.first, headerValue)
        }
    }

    appendMessageIdAsContentIdIfContentIdIsMissingOnTextXMLContentTypes(caseInsensitiveMap)
    if (headerNames.contains(MimeHeaders.CONTENT_DISPOSITION) && headerNames.contains(MimeHeaders.CONTENT_DESCRIPTION)) {
        appendContentDescriptionAsContentDispositionIfDispositionIsMissing(caseInsensitiveMap)
    }
}

private fun HeadersBuilder.appendMessageIdAsContentIdIfContentIdIsMissingOnTextXMLContentTypes(
    caseInsensitiveMap: CaseInsensitiveMap<String>
) {
    if (MimeUtility.unfold(caseInsensitiveMap[MimeHeaders.CONTENT_TYPE])?.contains("text/xml") == true) {
        if (caseInsensitiveMap[MimeHeaders.CONTENT_ID] != null) {
            log.warn(
                "Content-Id header allerede satt for text/xml: " + caseInsensitiveMap[MimeHeaders.CONTENT_ID] +
                    "\nMessage-Id: " + caseInsensitiveMap[SMTPHeaders.MESSAGE_ID]
            )
        } else {
            val headerValue = MimeUtility.unfold(caseInsensitiveMap[SMTPHeaders.MESSAGE_ID]!!.replace("\t", " "))
            append(MimeHeaders.CONTENT_ID, headerValue)
            log.info("Header: <${MimeHeaders.CONTENT_ID}> - <$headerValue>")
        }
    }
}

private fun HeadersBuilder.appendContentDescriptionAsContentDispositionIfDispositionIsMissing(
    caseInsensitiveMap: CaseInsensitiveMap<String>
) {
    if (caseInsensitiveMap[MimeHeaders.CONTENT_DESCRIPTION] != null && caseInsensitiveMap[MimeHeaders.CONTENT_DISPOSITION] == null) {
        val headerValue = MimeUtility.unfold(caseInsensitiveMap[MimeHeaders.CONTENT_DESCRIPTION]!!.replace("\t", " "))
        append(MimeHeaders.CONTENT_DISPOSITION, headerValue)
    }
}