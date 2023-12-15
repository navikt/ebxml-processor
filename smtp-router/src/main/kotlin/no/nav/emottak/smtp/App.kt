package no.nav.emottak.smtp


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
        get("/routeMail") {
            var routedMessagesCounterPair= Pair(0,0)
            Router(incomingStore, session).use {

                var current = Pair(0,0)
                val stop = Pair(0,0)
                    do {
                            current = it.routeMail().also {
                                routedMessagesCounterPair = Pair(routedMessagesCounterPair.first + current.first,routedMessagesCounterPair.second + current.second)
                            }
                    }while (current != stop)
                }
                .also {
                    call.respond(
                        HttpStatusCode.OK,
                        "Sent ${routedMessagesCounterPair.first} to old inbox & ${routedMessagesCounterPair.second} to new inbox")
                }


        }
    }

}
