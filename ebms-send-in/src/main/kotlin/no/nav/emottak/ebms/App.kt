package no.nav.emottak.ebms

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.emottak.fellesformat.addressing
import no.nav.emottak.fellesformat.wrapMessageInEIFellesFormat
import no.nav.emottak.frikort.frikortClient
import no.nav.emottak.frikort.frikortsporring
import no.nav.emottak.frikort.marshal
import no.nav.emottak.frikort.unmarshal
import no.nav.emottak.melding.model.SendInRequest
import no.nav.emottak.melding.model.SendInResponse
import no.nav.emottak.util.marker
import no.nav.security.token.support.v2.tokenValidationSupport
import no.nav.tjeneste.ekstern.frikort.v1.types.FrikortsporringRequest
import org.slf4j.LoggerFactory
import org.xmlsoap.schemas.soap.envelope.Envelope

internal val log = LoggerFactory.getLogger("no.nav.emottak.ebms.App")

fun main() {
    // val database = Database(mapHikariConfig(DatabaseConfig()))
    // database.migrate()
    System.setProperty("io.ktor.http.content.multipart.skipTempFile", "true")
    embeddedServer(Netty, port = 8080, module = Application::ebmsSendInModule, configure = {
        this.maxChunkSize = 100000
    }).start(wait = true)
}

fun Application.ebmsSendInModule() {
    install(ContentNegotiation) {
        json()
    }
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
    }

    install(Authentication) {
        tokenValidationSupport(AZURE_AD_AUTH, Security().config)
    }

    routing {
        get("/testFrikortEndepunkt") {
            val testCpaString = String(this::class.java.classLoader.getResource("frikortRequest.xml")!!.readBytes())
            val envelope = unmarshal(testCpaString, Envelope::class.java)
            val frikortSporting = envelope.body.any.first() as FrikortsporringRequest
            val response = frikortClient.frikortsporring(frikortSporting)
            log.info(marshal(response))
            call.respondText(marshal(response))
        }

        post("/fagmelding/synkron") {
            val request = this.call.receive(SendInRequest::class)
            runCatching {
                log.info(request.marker(), "Payload ${request.payloadId} videresendes")
                frikortsporring(wrapMessageInEIFellesFormat(request))
            }.onSuccess {
                log.info(request.marker(), "Payload ${request.payloadId} videresendt til fagsystem")
                call.respond(SendInResponse(request.messageId, request.conversationId, request.addressing.replayTo(it.eiFellesformat.mottakenhetBlokk.ebService, it.eiFellesformat.mottakenhetBlokk.ebAction), marshal(it.eiFellesformat.msgHead).toByteArray()))
            }.onFailure {
                log.error(request.marker(), "Payload ${request.payloadId} videresending feilet", it)
                call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
            }
        }

        authenticate(AZURE_AD_AUTH) {
            get("/test-auth") {
                log.info("Secure API '/test-auth' endpoint called")
                call.respondText("Hello World from a secure context")
            }
        }

        registerHealthEndpoints(appMicrometerRegistry)
    }
}
