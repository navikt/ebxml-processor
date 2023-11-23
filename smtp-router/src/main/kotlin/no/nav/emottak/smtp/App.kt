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
import jakarta.mail.internet.MimeUtility
import no.nav.emottak.constants.MimeHeaders
import no.nav.emottak.constants.SMTPHeaders
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
            val inbox = imapStore.getFolder("Inbox") as IMAPFolder
            val testdata = imapStore.getFolder("testdata") as IMAPFolder
            inbox.moveMessages(inbox.messages,testdata)
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
        }

        get("/mail/log/outgoing") {
            do {
                val messages = MailReader(bccStore).readMail()
            } while (messages.isNotEmpty())
        }
    }
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
