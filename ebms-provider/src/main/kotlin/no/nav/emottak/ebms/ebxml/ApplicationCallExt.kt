package no.nav.emottak.ebms.ebxml

import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.ContentType
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.ebms.log
import no.nav.emottak.ebms.model.DokumentType
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.validation.MimeHeaders
import no.nav.emottak.ebms.validation.MimeValidationException
import no.nav.emottak.ebms.validation.validateMimeAttachment
import no.nav.emottak.ebms.validation.validateMimeSoapEnvelope
import no.nav.emottak.ebms.xml.asByteArray
import no.nav.emottak.ebms.xml.asString
import no.nav.emottak.ebms.xml.getDocumentBuilder
import no.nav.emottak.message.model.EbmsAttachment
import no.nav.emottak.message.util.createUniqueMimeMessageId
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

@Throws(MimeValidationException::class)
suspend fun ApplicationCall.receiveEbmsDokument(): EbMSDocument {
    log.info("Parsing message with Message-Id: ${request.header(SMTPHeaders.MESSAGE_ID)}")

    return when (val contentType = this.request.contentType().withoutParameters()) {
        ContentType.parse("multipart/related") -> handleMultipartRelated()
        ContentType.parse("text/xml") -> handleTextXml()
        else -> {
            throw MimeValidationException("Ukjent request body med Content-Type $contentType")
        }
    }
}

private suspend fun ApplicationCall.handleTextXml(): EbMSDocument {
    val debugClearText = !request.header("cleartext").isNullOrBlank()
    val dokument = withContext(Dispatchers.IO) {
        if (debugClearText || "base64" != request.header(MimeHeaders.CONTENT_TRANSFER_ENCODING)?.lowercase()) {
            receive<ByteArray>()
        } else {
            Base64.getMimeDecoder()
                .decode(receive<ByteArray>())
        }
    }
    return EbMSDocument(
        this.request.headers[MimeHeaders.CONTENT_ID]!!.convertToValidatedContentID(),
        getDocumentBuilder().parse(ByteArrayInputStream(dokument)),
        emptyList()
    )
}

private suspend fun ApplicationCall.handleMultipartRelated(): EbMSDocument {
    val debugClearText = !request.header("cleartext").isNullOrBlank()
    val allParts = mutableListOf<PartData>().apply {
        receiveMultipart().forEachPart {
            var partDataToAdd = it
            if (it is PartData.FileItem) it.streamProvider.invoke()
            if (it is PartData.FormItem) {
                val boundary = request.contentType().parameter("boundary")
                if (it.value.contains("--$boundary--")) {
                    log.warn("Encountered KTOR bug, trimming boundary")
                    partDataToAdd = PartData.FormItem(it.value.substringBefore("--$boundary--").trim(), {}, it.headers)
                }
            }
            this.add(partDataToAdd)
            partDataToAdd.dispose.invoke()
            it.dispose.invoke()
        }
    }
    val start =
        request.contentType().withoutParameters().parameter("start") ?: allParts.first().headers[MimeHeaders.CONTENT_ID]
    val dokument = allParts.find { it.headers[MimeHeaders.CONTENT_ID] == start }!!.also {
        it.validateMimeSoapEnvelope()
    }.let {
        val contentID =
            it.headers[MimeHeaders.CONTENT_ID]?.convertToValidatedContentID() ?: "GENERERT-${UUID.randomUUID()}"
        val isBase64 = "base64".equals(it.headers[MimeHeaders.CONTENT_TRANSFER_ENCODING], true)
        Pair(contentID, it.payload(debugClearText || !isBase64))
    }
    val attachments = allParts.filter { it.headers[MimeHeaders.CONTENT_ID] != start }
    attachments.forEach { it.validateMimeAttachment() }

    return EbMSDocument(
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

private fun String.convertToValidatedContentID(): String {
    return Regex("""<(.*?)>""").find(this)?.groups?.get(1)?.value ?: this
}

suspend fun ApplicationCall.respondEbmsDokument(ebmsDokument: EbMSDocument) {
    if (ebmsDokument.dokumentType() == DokumentType.ACKNOWLEDGMENT) {
        log.info("Successfuly processed Payload Message")
    }

    this.response.headers.apply {
        this.append(MimeHeaders.SOAP_ACTION, "ebXML")
    }
    if (ebmsDokument.dokumentType() == DokumentType.PAYLOAD) {
        val ebxml = Base64.getMimeEncoder().encodeToString(ebmsDokument.dokument.asByteArray())
        val contentId = createUniqueMimeMessageId()
        val ebxmlFormItem = PartData.FormItem(
            ebxml,
            {},
            HeadersBuilder().apply {
                this.append(MimeHeaders.CONTENT_ID, "<$contentId>")
                this.append(MimeHeaders.CONTENT_TYPE, ContentType.Text.Xml.toString())
                this.append(MimeHeaders.CONTENT_TRANSFER_ENCODING, "base64")
            }.build()
        )
        val parts = mutableListOf<PartData>(ebxmlFormItem)
        ebmsDokument.attachments.first().let {
            PartData.FormItem(
                // Base64.getMimeEncoder().encodeToString(it.bytes), // Implicit ISO_8859_1
                String(Base64.getMimeEncoder().encode(it.bytes), StandardCharsets.UTF_8), // TODO verifiser
                {},
                HeadersBuilder().apply {
                    append(MimeHeaders.CONTENT_TRANSFER_ENCODING, "base64")
                    append(MimeHeaders.CONTENT_TYPE, it.contentType)
                    append(MimeHeaders.CONTENT_ID, "<${it.contentId}>")
                }.build()
            ).also {
                parts.add(it)
            }
        }
        this.response.headers.append(MimeHeaders.CONTENT_TRANSFER_ENCODING, "8bit")
        val boundary = "------=_Part" + System.currentTimeMillis() + "." + System.nanoTime()
        this.respond(
            HttpStatusCode.OK,
            MultiPartFormDataContent(
                parts,
                boundary,
                ContentType.parse("""multipart/related;boundary="$boundary";start="<$contentId>";type="text/xml"""")
            )
        )
    } else {
        this.response.headers.append(MimeHeaders.CONTENT_TYPE, ContentType.Text.Xml.toString())
        this.response.headers.append(MimeHeaders.CONTENT_TRANSFER_ENCODING, "8bit")
        this.respondText(status = HttpStatusCode.OK, text = ebmsDokument.dokument.asString())
    }
}

private fun PartData.payload(clearText: Boolean = false): ByteArray {
    return when (this) {
        is PartData.FormItem -> if (clearText) {
            return this.value.toByteArray()
        } else {
            try {
                Base64.getMimeDecoder().decode(this.value.trim())
            } catch (e: IllegalArgumentException) {
                log.warn("First characters in failing string: <${this.value.substring(0, 50)}>", e)
                log.warn("Last characters in failing string: <${this.value.takeLast(50)}>", e)
                throw e
            }
        }

        is PartData.FileItem -> {
            val stream = this.streamProvider.invoke()
            val bytes = stream.readAllBytes()
            stream.close()
            if (clearText) return bytes else Base64.getMimeDecoder().decode(bytes)
        }

        else -> byteArrayOf()
    }
}
