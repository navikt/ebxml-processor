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
import io.ktor.util.*
import jakarta.mail.internet.MimeUtility
import kotlinx.coroutines.runBlocking
import no.nav.emottak.constants.MimeHeaders
import no.nav.emottak.constants.SMTPHeaders
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
        get("/routeMail") {
            MailReader(incomingStore).routeMail()
                .also {
                    call.respond(HttpStatusCode.OK,
                        "Sent ${it.first} to old inbox & ${it.second} to new inbox")
                }
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
        if (MimeUtility.unfold(caseInsensitiveMap[MimeHeaders.CONTENT_TYPE])?.contains("text/xml") == true) {
            if (caseInsensitiveMap[MimeHeaders.CONTENT_ID] != null) {
                log.warn(
                    "Content-Id header allerede satt for text/xml: " + caseInsensitiveMap[MimeHeaders.CONTENT_ID]
                            + "\nMessage-Id: " + caseInsensitiveMap[SMTPHeaders.MESSAGE_ID]
                )
            }
            val headerValue = MimeUtility.unfold(caseInsensitiveMap[SMTPHeaders.MESSAGE_ID]!!.replace("\t", " "))
            append(MimeHeaders.CONTENT_ID, headerValue)
            log.info("Header: <${MimeHeaders.CONTENT_ID}> - <${headerValue}>")
        }
    }
}
