package no.nav.emottak.ebms

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.uri
import io.ktor.util.logging.KtorSimpleLogger
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import net.logstash.logback.marker.Markers
import no.nav.emottak.constants.SMTPHeaders
import java.time.Duration
import java.time.Instant
import kotlin.time.toKotlinDuration

internal fun Application.installMicrometerRegistry(appMicrometerRegistry: PrometheusMeterRegistry) {
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
    }
}

internal fun Application.installContentNegotiation() {
    install(ContentNegotiation) {
        json()
    }
}

internal fun Application.installRequestTimerPlugin() {
    install(
        createRouteScopedPlugin("RequestTimer") {
            val simpleLogger = KtorSimpleLogger("RequestTimerLogger")
            val timeableURIs = listOf("/ebms/sync")
            var startTime = Instant.now()
            onCall { call ->
                if (call.request.uri in timeableURIs) {
                    startTime = Instant.now()
                    simpleLogger.info("Received " + call.request.uri)
                }
            }
            onCallRespond { call ->
                if (call.request.uri in timeableURIs) {
                    val endTime = Duration.between(
                        startTime,
                        Instant.now()
                    )
                    simpleLogger.info(
                        Markers.appendEntries(
                            mapOf(
                                Pair("Headers", call.request.headers.actuallyUsefulToString()),
                                Pair("smtpMessageId", call.request.headers[SMTPHeaders.MESSAGE_ID] ?: "-"),
                                Pair("Endpoint", call.request.uri),
                                Pair("request_time", endTime.toMillis()),
                                Pair("httpStatus", call.response.status()?.value ?: 0)
                            )
                        ),
                        "Finished " + call.request.uri + " request. Processing time: " + endTime.toKotlinDuration()
                    )
                }
            }
        }
    )
}
