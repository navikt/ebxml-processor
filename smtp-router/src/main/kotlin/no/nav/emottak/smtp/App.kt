
package no.nav.emottak.smtp
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.*
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.*
import io.ktor.utils.io.core.*
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.internet.MimeUtility
import kotlinx.coroutines.runBlocking
import no.nav.emottak.constants.MimeHeaders
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.util.getEnvVar
import org.eclipse.angus.mail.imap.IMAPFolder
import org.slf4j.LoggerFactory
import kotlin.text.String

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::myApplicationModule).start(wait = true)
}

internal val log = LoggerFactory.getLogger("no.nav.emottak.smtp")
fun Application.myApplicationModule() {
    install(ContentNegotiation) {
        json()
    }
    routing {
        get("/test") {
            call.respond("Hello World!")
        }

        get("/mail/read") {
            val client = HttpClient(CIO)
            runCatching {
                MailReader(incomingStore, false).use {
                    log.info("read ${it.count()} from innbox")
                    do {
                        val messages = it.readMail()

                        messages.forEach { message ->
                            runCatching {
                                runBlocking {

                                    val partData: List<PartData> = emptyList()
                                    if (message.parts.size == 1 && message.headers.isEmpty()) {
                                        client.post("https://ebms-provider.intern.dev.nav.no/ebms") {
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
                                           message.parts.first().bytes)

                                    }
                                    }
                                    else {
                                        val partData: List<PartData> = message.parts.map {
                                            PartData.FormItem(String(it.bytes), {},Headers.build ( it.headers.filterHeader(
                                                MimeHeaders.CONTENT_TYPE
                                            )))
                                             PartData.FileItem({ByteReadPacket(message.parts.first().bytes)},{},Headers.build ( it.headers.filterHeader(
                                                MimeHeaders.CONTENT_TYPE
                                            )))
                                        }
                                        val contentType = message.headers[MimeHeaders.CONTENT_TYPE]!!
                                        val boundary = ContentType.parse(contentType).parameter("boundary")
                                        ContentDisposition.parse("").parameter("filename")


                                         client.post("https://ebms-provider.intern.dev.nav.no/ebms") {
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
                                         setBody(MultiPartFormDataContent(
            partData, boundary!!, ContentType.parse(contentType)
        ))

                                    }



                                    }


                                }
                            }.onFailure {
                                log.error(it.message, it)
                            }
                        }
                        log.info("Inbox has messages ${messages.isNotEmpty()}")
                    } while (messages.isNotEmpty())
                }
            }.onSuccess {
                call.respond(HttpStatusCode.OK, "Meldinger Lest")
            }.onFailure {
                log.error(it.message, it)
                call.respond(it.localizedMessage)
            }
            logBccMessages()
        }

        get("/mail/log/outgoing") {
            logBccMessages()
            call.respond(HttpStatusCode.OK)

        }
    }
}


   fun mapPartData(part: Part) : PartData? {
     return null
   }

fun logBccMessages() {
    val inbox = bccStore.getFolder("INBOX") as IMAPFolder
    val testDataInbox = bccStore.getFolder("testdata") as IMAPFolder
    testDataInbox.open(Folder.READ_WRITE)
    if (testDataInbox.messageCount > getEnvVar("INBOX_LIMIT", "2000").toInt()) {
        testDataInbox.messages.map {
            it.setFlag(Flags.Flag.DELETED, true)
            it
        }.toTypedArray().also {
            testDataInbox.expunge(it)
        }

    }
    inbox.open(Folder.READ_WRITE)
    inbox.messages.forEach {
        if (it.content is MimeMultipart) {
            runCatching {
                (it.content as MimeMultipart).getBodyPart(0)
            }.onSuccess {
                log.info(
                    "Incoming multipart request with headers ${
                        it.allHeaders.toList().map { it.name + ":" + it.value }
                    }" +
                            "with body ${String(it.inputStream.readAllBytes())}"
                )
            }
        } else {
            log.info("Incoming singlepart request ${String(it.inputStream.readAllBytes())}")
        }

    }.also {

        inbox.moveMessages(inbox.messages, testDataInbox)
    }
    inbox.close()
    testDataInbox.close()
}

fun Map<String, String>.filterHeader(vararg headerNames: String): HeadersBuilder.() -> Unit = {
    val caseInsensitiveMap = CaseInsensitiveMap<String>().apply {
        putAll(this@filterHeader)
    }
    headerNames.map {
        Pair(it, caseInsensitiveMap[it])
    }.forEach {
        if (it.second != null) {
            val headerValue = MimeUtility.unfold(it.second!!.replace("\t"," "))
            append(it.first, headerValue)
        }
    }
    if(MimeUtility.unfold(caseInsensitiveMap[MimeHeaders.CONTENT_TYPE])?.contains("text/xml") == true) {
        if(caseInsensitiveMap[MimeHeaders.CONTENT_ID] != null) {
            log.warn("Content-Id header allerede satt for text/xml: " + caseInsensitiveMap[MimeHeaders.CONTENT_ID]
                    + "\nMessage-Id: " + caseInsensitiveMap[SMTPHeaders.MESSAGE_ID])
        }
        val headerValue = MimeUtility.unfold(caseInsensitiveMap[SMTPHeaders.MESSAGE_ID]!!.replace("\t"," "))
        append(MimeHeaders.CONTENT_ID, headerValue)
        log.info("Header: <${MimeHeaders.CONTENT_ID}> - <${headerValue}>")
    }
}
