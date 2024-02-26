/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package no.nav.emottak.ebms

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HeadersBuilder
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.logstash.logback.marker.Markers
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.ebms.model.DokumentType
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.EbmsAttachment
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.ebms.validation.MimeHeaders
import no.nav.emottak.ebms.validation.MimeValidationException
import no.nav.emottak.ebms.validation.validateMimeAttachment
import no.nav.emottak.ebms.validation.validateMimeSoapEnvelope
import no.nav.emottak.ebms.xml.asString
import no.nav.emottak.ebms.xml.getDocumentBuilder
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.time.toKotlinDuration

val log = LoggerFactory.getLogger("no.nav.emottak.ebms.App")

fun logger() = log
fun main() {
    // val database = Database(mapHikariConfig(DatabaseConfig()))
    // database.migrate()
    System.setProperty("io.ktor.http.content.multipart.skipTempFile", "true")
    embeddedServer(Netty, port = 8080, module = Application::ebmsProviderModule, configure = {
        this.maxChunkSize = 100000
    }).start(wait = true)
}

fun defaultHttpClient(): () -> HttpClient {
    return {
        HttpClient(CIO) {
            expectSuccess = true
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
    }
}
fun PartData.payload(clearText: Boolean = false): ByteArray {
    return when (this) {
        is PartData.FormItem -> if (clearText) {
            return this.value.toByteArray()
        } else {
            java.util.Base64.getMimeDecoder().decode(this.value)
        }
        is PartData.FileItem -> {
            val stream = this.streamProvider.invoke()
            val bytes = stream.readAllBytes()
            stream.close()
            if (clearText) return bytes else java.util.Base64.getMimeDecoder().decode(bytes)
        }
        else -> byteArrayOf()
    }
}

fun Application.ebmsProviderModule() {
    val client = defaultHttpClient()
    val cpaClient = CpaRepoClient(client)
    val processingClient = PayloadProcessingClient(client)
    val sendInClient = SendInClient(client)
    val validator = DokumentValidator(cpaClient)
    val processing = ProcessingService(processingClient)
    val sendInService = SendInService(sendInClient)
    installRequestTimerPlugin()
    routing {
        get("/") {
            call.respondText("Hello, world!")
        }
        postEbmsAsync(validator, processing)
        postEbmsSyc(validator, processing, sendInService)
    }
}

private fun Application.installRequestTimerPlugin() {
    install(
        createRouteScopedPlugin("RequestTimer") {
            val simpleLogger = KtorSimpleLogger("RequestTimerLogger")
            var startTime = Instant.now()
            onCall { call ->
                startTime = Instant.now()
                simpleLogger.info("Received " + call.request.uri)
            }
            onCallRespond { call ->
                val endTime = Duration.between(
                    startTime,
                    Instant.now()
                )
                simpleLogger.info(
                    Markers.appendEntries(
                        mapOf(
                            Pair("smtpMessageId", call.request.headers[SMTPHeaders.MESSAGE_ID] ?: "-"),
                            Pair("Endpoint", call.request.uri),
                            Pair("RequestTime", endTime.toMillis())
                        )
                    ),
                    "Finished " + call.request.uri + " request. Processing time: " + endTime.toKotlinDuration()
                )
            }
        }
    )
}

@Throws(MimeValidationException::class)
suspend fun ApplicationCall.receiveEbmsDokument(): EbMSDocument {
    log.info("Parsing message with Message-Id: ${request.header(SMTPHeaders.MESSAGE_ID)}")
    val debugClearText = !request.header("cleartext").isNullOrBlank()
    return when (val contentType = this.request.contentType().withoutParameters()) {
        ContentType.parse("multipart/related") -> {
            val allParts = mutableListOf<PartData>().apply {
                this@receiveEbmsDokument.receiveMultipart().forEachPart {
                    if (it is PartData.FileItem) it.streamProvider.invoke()
                    if (it is PartData.FormItem) it.value
                    this.add(it)
                    it.dispose.invoke()
                }
            }

            val dokument = allParts.find {
                it.contentType?.withoutParameters() == ContentType.parse("text/xml") && it.contentDisposition == null
            }.also {
                it?.validateMimeSoapEnvelope()
                    ?: throw MimeValidationException("Unable to find soap envelope multipart Message-Id ${this.request.header(SMTPHeaders.MESSAGE_ID)}")
            }!!.let {
                val contentID = it.headers[MimeHeaders.CONTENT_ID]!!.convertToValidatedContentID()
                val isBase64 = "base64" == it.headers[MimeHeaders.CONTENT_TRANSFER_ENCODING]
                Pair(contentID, it.payload(debugClearText || !isBase64))
            }
            val attachments =
                allParts.filter { it.contentDisposition?.disposition == ContentDisposition.Attachment.disposition }
            attachments.forEach {
                it.validateMimeAttachment()
            }
            EbMSDocument(
                dokument.first,
                getDocumentBuilder().parse(ByteArrayInputStream(dokument.second)),
                attachments.map {
                    EbmsAttachment(
                        it.payload(),
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
                    java.util.Base64.getMimeDecoder()
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

suspend fun ApplicationCall.respondEbmsDokument(ebmsDokument: EbMSDocument) {
    val payload = ebmsDokument
        .dokument
        .asString()
    //  .encodeBase64()
    if (ebmsDokument.dokumentType() == DokumentType.ACKNOWLEDGMENT) {
        log.info("Successfuly processed Payload Message")
    }
    val ebxml = Base64.getMimeEncoder().encodeToString(ebmsDokument.dokument.asString().toByteArray())

    this.response.headers.append(MimeHeaders.SOAP_ACTION, "ebxml")
    this.response.headers.append(MimeHeaders.CONTENT_TRANSFER_ENCODING, "8bit")
    if (ebmsDokument.dokumentType() == DokumentType.PAYLOAD) {
        val ebxmlFormItem = PartData.FormItem(ebxml, {}, HeadersBuilder().build())
        val parts = mutableListOf<PartData>(ebxmlFormItem)
        parts.add(PartData.FormItem(Base64.getMimeEncoder().encodeToString(ebmsDokument.attachments.first().dataSource), {}, HeadersBuilder().build()))
        this.respond(
            MultiPartFormDataContent(
                parts,
                "------=_Part" + System.currentTimeMillis() + "." + System.nanoTime(),
                ContentType.parse("""multipart/related;type="text/xml"""")
            )
        )
    } else {
        this.respondText(ebxml)
    }

    /*
    this.respondBytesWriter {

        MultiPartFormDataContent(
                                                        listOf(partData),
                                                        "123",
                                                        ContentType.parse("multipart/related")
                                                    ).writeTo(this)
    }*/

    // this.respondText(payload, ContentType.parse("text/xml"))
}
