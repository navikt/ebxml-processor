package no.nav.emottak;

import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.emottak.melding.Processor
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.Melding
import no.nav.emottak.melding.model.Part
import org.slf4j.event.Level
import java.util.UUID

val processor = Processor()

fun main() {
    embeddedServer(Netty, port = 8080) {
        serverSetup()
    }.start(wait = true)
}

private fun Application.serverSetup() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(ContentNegotiation) {
        json()
    }
    routing {
        registerHealthEndpoints()

        get("/payload/test") {
            val testByteArray = this::class.java.classLoader.getResource("xml/test.xml").readBytes()
            val testKrypteringSertifikat = this::class.java.classLoader.getResource("xml/cert.pem").readBytes()
            val testSigneringSertifikat = this::class.java.classLoader.getResource("xml/cert.pem").readBytes()
            val melding = Melding(
                header = Header(
                    messageId = UUID.randomUUID().toString(),
                    conversationId = UUID.randomUUID().toString(),
                    to = Part(
                        krypteringSertifikat = testKrypteringSertifikat,
                        signeringSertifikat = testSigneringSertifikat,
                        "12345"
                    ),
                    from = Part(
                        krypteringSertifikat = testKrypteringSertifikat,
                        signeringSertifikat = testSigneringSertifikat,
                        "54321"
                    ),
                    role = "",
                    service = "",
                    action = ""
                ),
                originalPayload = testByteArray,
                processedPayload = testByteArray
            )
            val prosessertMelding = processor.processOutgoing(melding)
            call.respond(prosessertMelding)
        }

        post("/payload") {
            call.receiveMultipart().forEachPart {
                print(it is PartData.BinaryItem)
                print(it is PartData.FileItem)
            }
            call.respondText("Hello")
        }

        post("/payload/incoming") {
            call.receiveMultipart().forEachPart {
                print(it is PartData.BinaryItem)
                print(it is PartData.FileItem)
            }
            call.respondText("Hello")
        }

        post("/payload/outgoing") {
            call.receiveMultipart().forEachPart {
                print(it is PartData.BinaryItem)
                print(it is PartData.FileItem)
            }
            call.respondText("Hello")
        }
    }
}
