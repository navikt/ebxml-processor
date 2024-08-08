package no.nav.emottak.ebms

import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorRuntime
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
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.Timer.ResourceSample
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.auth.AZURE_AD_AUTH
import no.nav.emottak.auth.AuthConfig
import no.nav.emottak.fellesformat.wrapMessageInEIFellesFormat
import no.nav.emottak.frikort.frikortsporring
import no.nav.emottak.frikort.marshal
import no.nav.emottak.melding.model.SendInRequest
import no.nav.emottak.melding.model.SendInResponse
import no.nav.emottak.pasientliste.pasientlisteRequest
import no.nav.emottak.util.getEnvVar
import no.nav.emottak.util.marker
import no.nav.security.token.support.v2.tokenValidationSupport
import org.slf4j.LoggerFactory

internal val log = LoggerFactory.getLogger("no.nav.emottak.ebms.App")

fun main() {
    // val database = Database(mapHikariConfig(DatabaseConfig()))
    // database.migrate()

    System.setProperty("io.ktor.http.content.multipart.skipTempFile", "true")
    embeddedServer(Netty, port = 8080, module = Application::ebmsSendInModule, configure = {
        this.maxChunkSize = 100000
    }).start(wait = true)
}

fun <T> timed(meterRegistry: PrometheusMeterRegistry, metricName: String, process: ResourceSample.() -> T): T =
    io.micrometer.core.instrument.Timer.resource(meterRegistry, metricName)
        .use {
            process(it)
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
        tokenValidationSupport(AZURE_AD_AUTH, AuthConfig.getTokenSupportConfig())
    }

    if (getEnvVar("NAIS_CLUSTER_NAME", "local") != "prod-fss") {
        DecoroutinatorRuntime.load()
    }

    routing {
        authenticate(AZURE_AD_AUTH) {
            post("/fagmelding/synkron") {
                val request = this.call.receive(SendInRequest::class)
                val service = request.addressing.service
                if (service == "PasientlisteForesporsel") {
                    runCatching {
                        log.info(
                            request.marker(),
                            "PasientlisteForesporsel: Payload ${request.payloadId} videresendes til fagsystem"
                        )
                        withContext(Dispatchers.IO) {
                            // TODO: Legg til timer
                            pasientlisteRequest(wrapMessageInEIFellesFormat(request))
                        }
                    }.onSuccess {
                        log.trace(
                            request.marker(),
                            "Payload ${request.payloadId} videresending til fagsystem ferdig, svar mottatt og returnerert"
                        )
                        call.respond(
                            SendInResponse(
                                request.messageId,
                                request.conversationId,
                                request.addressing.replayTo(
                                    it.mottakenhetBlokk.ebService,
                                    it.mottakenhetBlokk.ebAction
                                ),
                                marshal(it.msgHead).toByteArray()
                            )
                        )
                    }.onFailure {
                        log.error(request.marker(), "Payload ${request.payloadId} videresending feilet", it)
                        call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
                    }
                } else if (service == "HarBorgerEgenandelFritak" || service == "HarBorgerFrikort") {
                    runCatching {
                        log.info(request.marker(), "Payload ${request.payloadId} videresendes til fagsystem")
                        withContext(Dispatchers.IO) {
                            timed(appMicrometerRegistry, "frikort-sporing") {
                                frikortsporring(wrapMessageInEIFellesFormat(request))
                            }
                        }
                    }.onSuccess {
                        log.trace(
                            request.marker(),
                            "Payload ${request.payloadId} videresending til fagsystem ferdig, svar mottatt og returnerert"
                        )
                        call.respond(
                            SendInResponse(
                                request.messageId,
                                request.conversationId,
                                request.addressing.replayTo(
                                    it.eiFellesformat.mottakenhetBlokk.ebService,
                                    it.eiFellesformat.mottakenhetBlokk.ebAction
                                ),
                                marshal(it.eiFellesformat.msgHead).toByteArray()
                            )
                        )
                    }.onFailure {
                        log.error(request.marker(), "Payload ${request.payloadId} videresending feilet", it)
                        call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
                    }
                } else {
                    log.error("Service '$service' is not implemented")
                    throw NotImplementedError("Service $service is not implemented")
                }
            }
        }

        registerHealthEndpoints(appMicrometerRegistry)
    }
}
