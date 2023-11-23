package no.nav.emottak.smtp

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpStatusCode
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
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.internet.MimeUtility
import no.nav.emottak.constants.MimeHeaders
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.util.getEnvVar
import org.eclipse.angus.mail.imap.IMAPFolder
import org.slf4j.LoggerFactory

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
            val client = HttpClient(CIO) {
                expectSuccess = true
            }
            runCatching {
                MailReader(incomingStore, false).use {
                    do {
                        val messages = it.readMail()

                        messages.forEach { message ->
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
                                    message.bytes
                                )
                            }
                        }
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

fun logBccMessages() {
     val inbox = bccStore.getFolder("INBOX") as IMAPFolder
                val testDataInbox = bccStore.getFolder("testdata") as IMAPFolder
                testDataInbox.open(Folder.READ_WRITE)
                if (testDataInbox.messageCount > getEnvVar("INBOX_LIMIT", "2000").toInt() )  {
                    testDataInbox.messages.map {
                         it.setFlag(Flags.Flag.DELETED,true)
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

                     inbox.moveMessages(inbox.messages,testDataInbox)
                }
            inbox.close()
            testDataInbox.close()
}

fun Map<String, String>.filterHeader(vararg headerNames: String): HeadersBuilder.() -> Unit = {
    headerNames.map {
        Pair(it, this@filterHeader[it])
    }.forEach {
        if (it.second != null) {
            val headerValue = MimeUtility.unfold(it.second!!)
            append(it.first, headerValue)
            log.info("Header: <${it.first}> - <${headerValue}>")
        }
    }
}