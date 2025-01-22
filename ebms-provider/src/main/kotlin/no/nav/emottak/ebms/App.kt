/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package no.nav.emottak.ebms

import com.zaxxer.hikari.HikariConfig
import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorRuntime
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import net.logstash.logback.marker.Markers
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.ebms.persistence.Database
import no.nav.emottak.ebms.persistence.EbmsMessageRepository
import no.nav.emottak.ebms.persistence.ebmsDbConfig
import no.nav.emottak.ebms.persistence.ebmsMigrationConfig
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.util.getEnvVar
import no.nav.emottak.util.isProdEnv
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import kotlin.time.toKotlinDuration

val log = LoggerFactory.getLogger("no.nav.emottak.ebms.App")

fun logger() = log
fun main() {
    // val database = Database(mapHikariConfig(DatabaseConfig()))
    // database.migrate()
    System.setProperty("io.ktor.http.content.multipart.skipTempFile", "true")
    if (getEnvVar("NAIS_CLUSTER_NAME", "local") != "prod-fss") {
        DecoroutinatorRuntime.load()
    }
    embeddedServer(
        Netty,
        port = 8080,
        module = { ebmsProviderModule(ebmsDbConfig.value, ebmsMigrationConfig.value) },
        configure = {
            this.maxChunkSize = 100000
        }
    ).start(wait = true)
}

fun Application.ebmsProviderModule(
    dbConfig: HikariConfig,
    migrationConfig: HikariConfig
) {
    val database = Database(dbConfig)
    database.migrate(migrationConfig)

    val ebmsMessageRepository = EbmsMessageRepository(database)

    val cpaClient = CpaRepoClient(defaultHttpClient())
    val validator = DokumentValidator(cpaClient)

    val processingClient = PayloadProcessingClient(scopedAuthHttpClient(EBMS_PAYLOAD_SCOPE))
    val processing = ProcessingService(processingClient)

    val sendInClient = SendInClient(scopedAuthHttpClient(EBMS_SEND_IN_SCOPE))
    val sendInService = SendInService(sendInClient)

    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    installMicrometerRegistry(appMicrometerRegistry)
    installRequestTimerPlugin()

    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/") {
            call.respondText("Hello, world!")
        }
        if (!isProdEnv()) {
            packageEbxml(validator, processing)
            unpackageEbxml(validator, processing)
        }
        registerHealthEndpoints(appMicrometerRegistry)
        navCheckStatus()
        postEbmsAsync(validator, processing, ebmsMessageRepository)
        postEbmsSync(validator, processing, sendInService, ebmsMessageRepository)
    }
}

private fun Application.installMicrometerRegistry(appMicrometerRegistry: PrometheusMeterRegistry) {
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
    }
}

private fun Application.installRequestTimerPlugin() {
    install(
        createRouteScopedPlugin("RequestTimer") {
            val simpleLogger = KtorSimpleLogger("RequestTimerLogger")
            val timeableURIs = listOf("/ebms/sync", "/ebms/async")
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
