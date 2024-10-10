package no.nav.emottak.ebms.ebxml

import io.ktor.http.ContentType
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.ebms.log
import no.nav.emottak.ebms.logger
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.payload
import no.nav.emottak.ebms.validation.MimeHeaders
import no.nav.emottak.ebms.validation.MimeValidationException
import no.nav.emottak.ebms.validation.validateMimeAttachment
import no.nav.emottak.ebms.validation.validateMimeSoapEnvelope
import no.nav.emottak.ebms.xml.getDocumentBuilder
import no.nav.emottak.melding.model.EbmsAttachment
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.UUID

@Throws(MimeValidationException::class)
suspend fun ApplicationCall.receiveEbmsDokument(): EbMSDocument {
    log.info("Parsing message with Message-Id: ${request.header(SMTPHeaders.MESSAGE_ID)}")
    val debugClearText = !request.header("cleartext").isNullOrBlank()
    return when (val contentType = this.request.contentType().withoutParameters()) {
        ContentType.parse("multipart/related") -> {
            val allParts = mutableListOf<PartData>().apply {
                this@receiveEbmsDokument.receiveMultipart().forEachPart {
                    var partDataToAdd = it
                    if (it is PartData.FileItem) it.streamProvider.invoke()
                    if (it is PartData.FormItem) {
                        val boundary = this@receiveEbmsDokument.request.contentType().parameter("boundary")
                        if (it.value.contains("--$boundary--")) {
                            logger().warn("Encountered KTOR bug, trimming boundary")
                            partDataToAdd =
                                PartData.FormItem(it.value.substringBefore("--$boundary--").trim(), {}, it.headers)
                        }
                    }
                    this.add(partDataToAdd)
                    partDataToAdd.dispose.invoke()
                    it.dispose.invoke()
                }
            }
            val start = contentType.parameter("start") ?: allParts.first().headers[MimeHeaders.CONTENT_ID]

            val dokument = allParts.find {
                it.headers[MimeHeaders.CONTENT_ID] == start
            }!!.also {
                it.validateMimeSoapEnvelope()
            }.let {
                val contentID =
                    it.headers[MimeHeaders.CONTENT_ID]?.convertToValidatedContentID() ?: "GENERERT-${UUID.randomUUID()}"
                val isBase64 = "base64".equals(it.headers[MimeHeaders.CONTENT_TRANSFER_ENCODING], true)
                Pair(contentID, it.payload(debugClearText || !isBase64))
            }

            val attachments =
                allParts.filter { it.headers[MimeHeaders.CONTENT_ID] != start }
            attachments.forEach {
                it.validateMimeAttachment()
            }

            EbMSDocument(
                dokument.first,
                getDocumentBuilder().parse(ByteArrayInputStream(dokument.second)),
                attachments.map {
                    val isBase64 = "base64".equals(it.headers[MimeHeaders.CONTENT_TRANSFER_ENCODING], true)
                    EbmsAttachment(
                        it.payload(debugClearText || !isBase64),
                        it.contentType!!.contentType,
                        it.headers[MimeHeaders.CONTENT_ID]!!.convertToValidatedContentID()
                    )
                }
            )
        }

        ContentType.parse("text/xml") -> {
            val dokument = withContext(Dispatchers.IO) {
                if (debugClearText || "base64" != request.header(MimeHeaders.CONTENT_TRANSFER_ENCODING)?.lowercase()) {
                    this@receiveEbmsDokument.receive<ByteArray>()
                } else {
                    Base64.getMimeDecoder()
                        .decode(this@receiveEbmsDokument.receive<ByteArray>())
                }
            }
            EbMSDocument(
                this.request.headers[MimeHeaders.CONTENT_ID]!!.convertToValidatedContentID(),
                getDocumentBuilder().parse(ByteArrayInputStream(dokument)),
                emptyList()
            )
        }

        else -> {
            throw MimeValidationException("Ukjent request body med Content-Type $contentType")
            // call.respond(HttpStatusCode.BadRequest, "Ukjent request body med Content-Type $contentType")
            // return@post
        }
    }
}

private fun String.convertToValidatedContentID(): String {
    return Regex("""<(.*?)>""").find(this)?.groups?.get(1)?.value ?: this
}
