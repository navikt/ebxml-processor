package no.nav.emottak.ebms

import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorRuntime
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.streamProvider
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import net.logstash.logback.marker.Markers
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.ebms.model.DokumentType
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.ebms.validation.MimeHeaders
import no.nav.emottak.ebms.xml.asByteArray
import no.nav.emottak.ebms.xml.asString
import no.nav.emottak.util.createUniqueMimeMessageId
import no.nav.emottak.util.getEnvVar
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.time.toKotlinDuration

val log = LoggerFactory.getLogger("no.nav.emottak.ebms.App")

fun logger() = log
fun main() {
    // val database = Database(mapHikariConfig(DatabaseConfig()))
    // database.migrate()
    System.setProperty("io.ktor.http.content.multipart.skipTempFile", "true")
    if (getEnvVar("NAIS_CLUSTER_NAME", "local") != "prod-fss") {
        DecoroutinatorRuntime.load()
    }
    embeddedServer(Netty, port = 8080, module = Application::ebmsProviderModule, configure = {
        this.maxChunkSize = 100000
    }).start(wait = true)
}

fun PartData.payload(clearText: Boolean = false): ByteArray {
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

fun Application.ebmsProviderModule() {
    val cpaClient = CpaRepoClient(defaultHttpClient())
    val validator = DokumentValidator(cpaClient)

    val processingClient = PayloadProcessingClient(scopedAuthHttpClient(EBMS_PAYLOAD_SCOPE))
    val processing = ProcessingService(processingClient)

    val sendInClient = SendInClient(scopedAuthHttpClient(EBMS_SEND_IN_SCOPE))
    val sendInService = SendInService(sendInClient)

    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    installMicrometerRegistry(appMicrometerRegistry)
    installRequestTimerPlugin()

    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/") {
            call.respondText("Hello, world!")
        }

        registerHealthEndpoints(appMicrometerRegistry)
        navCheckStatus()
        postEbmsAsync(validator, processing)
        postEbmsSync(validator, processing, sendInService)
    }
}

private fun Application.installMicrometerRegistry(appMicrometerRegistry: PrometheusMeterRegistry) {
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
    }
}

private fun Application.installRequestTimerPlugin() {
    install(
        createRouteScopedPlugin("RequestTimer") {
            val simpleLogger = KtorSimpleLogger("RequestTimerLogger")
            val timeableURIs = listOf("/ebms/sync")
            var startTime = Instant.now()
            onCall { call ->
                if (call.request.uri in timeableURIs) {
                    startTime = Instant.now()
                    simpleLogger.info("Received " + call.request.uri)
                }
            }
            onCallRespond { call ->
                if (call.request.uri in timeableURIs) {
                    val endTime = Duration.between(
                        startTime,
                        Instant.now()
                    )
                    simpleLogger.info(
                        Markers.appendEntries(
                            mapOf(
                                Pair("Headers", call.request.headers.actuallyUsefulToString()),
                                Pair("smtpMessageId", call.request.headers[SMTPHeaders.MESSAGE_ID] ?: "-"),
                                Pair("Endpoint", call.request.uri),
                                Pair("request_time", endTime.toMillis()),
                                Pair("httpStatus", call.response.status()?.value ?: 0)
                            )
                        ),
                        "Finished " + call.request.uri + " request. Processing time: " + endTime.toKotlinDuration()
                    )
                }
            }
        }
    )
}

fun Headers.actuallyUsefulToString(): String {
    val sb = StringBuilder()
    entries().forEach {
        sb.append(it.key).append(":")
            .append("[${it.value.joinToString()}]\n")
    }
    return sb.toString()
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
