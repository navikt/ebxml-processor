package no.nav.emottak.payload

import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorRuntime
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.nav.emottak.melding.apprec.createNegativeApprec
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.Feil
import no.nav.emottak.melding.model.Payload
import no.nav.emottak.melding.model.PayloadRequest
import no.nav.emottak.melding.model.PayloadResponse
import no.nav.emottak.payload.util.marshal
import no.nav.emottak.payload.util.unmarshal
import no.nav.emottak.util.getEnvVar
import no.nav.emottak.util.marker
import org.slf4j.LoggerFactory
import java.util.UUID

val processor = Processor()
internal val log = LoggerFactory.getLogger("no.nav.emottak.payload")
fun main() {
    if (getEnvVar("NAIS_CLUSTER_NAME", "local") != "prod-fss") {
        DecoroutinatorRuntime.load()
    }
    embeddedServer(Netty, port = 8080) {
        serverSetup()
    }.start(wait = true)
}

private fun Application.serverSetup() {
    install(ContentNegotiation) {
        json()
    }
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
    }
    routing {
        registerHealthEndpoints(appMicrometerRegistry)

        post("/payload") {
            val request: PayloadRequest = call.receive(PayloadRequest::class)
            runCatching {
                log.info(request.marker(), "Payload ${request.payload.contentId} mottatt for prosessering")
                processor.process(request)
            }.onSuccess {
                log.info(request.marker(), "Payload ${request.payload.contentId} prosessert OK")
                call.respond(it)
            }.onFailure { originalError ->
                log.error(request.marker(), "Payload ${request.payload.contentId} prosessert med feil: ${originalError.message}", originalError)
                val shouldRespondWithNegativeAppRec = request.processing.processConfig?.apprec ?: false

                runCatching {
                    when (shouldRespondWithNegativeAppRec) {
                        true -> {
                            log.info(request.marker(), "Oppretter negativ AppRec for payload ${request.payload.contentId}")
                            val msgHead = unmarshal(request.payload.bytes, MsgHead::class.java)
                            val apprec = createNegativeApprec(msgHead, originalError as Exception)
                            Payload(marshal(apprec).toByteArray(), ContentType.Application.Xml.toString(), UUID.randomUUID().toString())
                        }
                        false -> null
                    }
                }.onSuccess {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        PayloadResponse(
                            processedPayload = it,
                            error = Feil(ErrorCode.UNKNOWN, originalError.localizedMessage, "Error"),
                            apprec = shouldRespondWithNegativeAppRec
                        )
                    )
                }.onFailure {
                    log.error(request.marker(), "Opprettelse av negativ apprec feilet", it)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        PayloadResponse(
                            error = Feil(ErrorCode.UNKNOWN, it.localizedMessage, "Error")
                        )
                    )
                }
            }
        }
    }
}
