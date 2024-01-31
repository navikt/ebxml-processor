package no.nav.emottak.ebms

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.emottak.frikort.FrikortClient
import no.nav.emottak.frikort.unmarshal
import no.nav.tjeneste.ekstern.frikort.v1.types.FrikortsporringRequest
import org.xmlsoap.schemas.soap.envelope.Envelope

fun main() {
    // val database = Database(mapHikariConfig(DatabaseConfig()))
    // database.migrate()
    System.setProperty("io.ktor.http.content.multipart.skipTempFile", "true")
    embeddedServer(Netty, port = 8080, module = Application::ebmsSendInModule, configure = {
        this.maxChunkSize = 100000
    }).start(wait = true)
}

fun Application.ebmsSendInModule() {
    routing {
        get("/") {
            call.respondText("Hello, world!")
        }
        get("/testFrikortEndepunkt") {
            val testCpaString = String(this::class.java.classLoader.getResource("frikort-soap.xml")!!.readBytes())
            val envelope = unmarshal(testCpaString, Envelope::class.java)
            val frikortSporting = envelope.body.any.first() as FrikortsporringRequest
            val frikortEndpoint = FrikortClient().fikortEndpoint()
            frikortEndpoint.frikortsporring(frikortSporting)
            println(envelope)
            call.respondText("Hello, world!")
        }
    }
}
