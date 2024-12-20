/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package no.nav.emottak.ebms

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.core.raise.result
import arrow.fx.coroutines.resourceScope
import com.zaxxer.hikari.HikariConfig
import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorRuntime
import io.github.nomisRev.kafka.receiver.AutoOffsetReset
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.CancellationException
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import net.logstash.logback.marker.Markers
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.ebms.configuration.Kafka
import no.nav.emottak.ebms.configuration.config
import no.nav.emottak.ebms.configuration.toProperties
import no.nav.emottak.ebms.persistence.Database
import no.nav.emottak.ebms.persistence.ebmsDbConfig
import no.nav.emottak.ebms.persistence.ebmsMigrationConfig
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.processing.SignalProcessor
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.util.getEnvVar
import no.nav.emottak.util.isProdEnv
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

val log = LoggerFactory.getLogger("no.nav.emottak.ebms.App")

fun logger() = log
fun main() = SuspendApp {
    // val database = Database(mapHikariConfig(DatabaseConfig()))
    // database.migrate()
    System.setProperty("io.ktor.http.content.multipart.skipTempFile", "true")
    if (getEnvVar("NAIS_CLUSTER_NAME", "local") != "prod-fss") {
        DecoroutinatorRuntime.load()
    }
    val config = config()
    result {
        resourceScope {
            server(
                Netty,
                port = 8080,
                module = { ebmsProviderModule(ebmsDbConfig.value, ebmsMigrationConfig.value) },
                configure = {
                    this.maxChunkSize = 100000
                }
            )

            if (getEnvVar("ASYNC_RECEIVER", "false").toBoolean()) {
                startSignalReceiver(config.kafka)
            }

            awaitCancellation()
        }
    }
        .onFailure { error ->
            when (error) {
                is CancellationException -> {} // expected behaviour - normal shutdown
                else -> log.error("Unexpected shutdown initiated", error)
            }
        }
}

fun startSignalReceiver(kafka: Kafka) = CoroutineScope(Dispatchers.Default).launch {
    log.debug("Starting signal message receiver")
    val receiverSettings: ReceiverSettings<String, ByteArray> =
        ReceiverSettings(
            bootstrapServers = kafka.bootstrapServers,
            keyDeserializer = StringDeserializer(),
            valueDeserializer = ByteArrayDeserializer(),
            groupId = kafka.groupId,
            pollTimeout = 30.seconds,
            autoOffsetReset = AutoOffsetReset.Earliest, // TODO set this to something else
            properties = kafka.toProperties()
        )

    val signalProcessor = SignalProcessor()
    KafkaReceiver(receiverSettings)
        .receive(kafka.incomingSignalTopic)
        .collect { record ->
            signalProcessor.processSignal(record.key(), record.value())
            record.offset.acknowledge().also {
                log.debug("Acknowledged topic offset ${record.offset()}")
            }
        }
}

fun Application.ebmsProviderModule(
    dbConfig: HikariConfig,
    migrationConfig: HikariConfig
) {
    val database = Database(dbConfig)
    database.migrate(migrationConfig)

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
        postEbmsAsync(validator, processing)
        postEbmsSync(validator, processing, sendInService)
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
