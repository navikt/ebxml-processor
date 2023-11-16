package no.nav.emottak.smtp

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
                    messages.forEach {
                        client.post("https://ebms-provider.intern.dev.nav.no") {
                            setBody(
                                it
                            )
                        }
                    }
                } while(messages.isNotEmpty())
            }.onSuccess {
                call.respond(HttpStatusCode.OK, "Meldinger Lest")
            }.onFailure {
                call.respond(it.localizedMessage)
            }
        }
    }
}