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
                do {
                    val messages = MailReader(store).readMail()
                    messages.forEach { message ->
                        client.post("https://ebms-provider.intern.dev.nav.no/ebms") {
                            headers (
                                message.headers.filterHeader(
                                    MimeHeaders.MIME_VERSION,
                                    MimeHeaders.CONTENT_ID,
                                    MimeHeaders.SOAP_ACTION,
                                    MimeHeaders.CONTENT_TYPE,
                                    "From",
                                    "To",
                                    "Message-Id",
                                    "Date"
                                )
                            )
                            setBody(
                                String(message.bytes)
                            )
                        }
                    }
                } while(messages.isNotEmpty())
            }.onSuccess {
                call.respond(HttpStatusCode.OK, "Meldinger Lest")
            }.onFailure {
                log.error(it.message,it)
                call.respond(it.localizedMessage)
            }
        }
    }
}

fun Map<String,String>.filterHeader(vararg headerNames: String): HeadersBuilder.() -> Unit = {
        headerNames.map {
            Pair(it,  this@filterHeader[it])
        }.forEach {
            if (it.second != null) {
                val headerValue = MimeUtility.unfold(it.second!!)
                append(it.first, headerValue )
                log.info("Header: <${it.first}> - <${headerValue}>")
            }
        }
}

object MimeHeaders {
    const val MIME_VERSION                  = "MIME-Version"
    const val SOAP_ACTION                   = "SOAPAction"
    const val CONTENT_TYPE                  = "Content-Type"
    const val CONTENT_ID                    = "Content-Id"
    const val CONTENT_TRANSFER_ENCODING     = "Content-Transfer-Encoding"
    const val CONTENT_DISPOSITION           = "Content-Disposition"
}