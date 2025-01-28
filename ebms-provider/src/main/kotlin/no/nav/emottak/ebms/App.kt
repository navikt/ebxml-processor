/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package no.nav.emottak.ebms

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.core.raise.result
import arrow.fx.coroutines.resourceScope
import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorRuntime
import io.ktor.server.application.Application
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.utils.io.CancellationException
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import no.nav.emottak.ebms.configuration.config
import no.nav.emottak.ebms.messaging.EbmsSignalProducer
import no.nav.emottak.ebms.messaging.startSignalReceiver
import no.nav.emottak.ebms.persistence.Database
import no.nav.emottak.ebms.persistence.ebmsDbConfig
import no.nav.emottak.ebms.persistence.ebmsMigrationConfig
import no.nav.emottak.ebms.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.processing.SignalProcessor
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.util.getEnvVar
import org.slf4j.LoggerFactory

val log = LoggerFactory.getLogger("no.nav.emottak.ebms.App")

fun logger() = log
fun main() = SuspendApp {
    System.setProperty("io.ktor.http.content.multipart.skipTempFile", "true")
    if (getEnvVar("NAIS_CLUSTER_NAME", "local") != "prod-fss") {
        DecoroutinatorRuntime.load()
    }

    val database = Database(ebmsDbConfig.value)
    database.migrate(ebmsMigrationConfig.value)

    val ebmsMessageDetailsRepository = EbmsMessageDetailsRepository(database)

    val config = config()
    if (config.kafkaSignalReceiver.active) {
        launch(Dispatchers.IO) {
            val cpaClient = CpaRepoClient(defaultHttpClient())
            val validator = DokumentValidator(cpaClient)
            val signalProcessor = SignalProcessor(ebmsMessageDetailsRepository, validator)
            startSignalReceiver(config.kafkaSignalReceiver.topic, config.kafka, signalProcessor)
        }
    }
    result {
        resourceScope {
            server(
                Netty,
                port = 8080,
                module = { ebmsProviderModule(ebmsMessageDetailsRepository) },
                configure = {
                    this.maxChunkSize = 100000
                }
            )
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

fun Application.ebmsProviderModule(
    ebmsMessageDetailsRepository: EbmsMessageDetailsRepository
) {
    val config = config()

    val ebmsSignalProducer = EbmsSignalProducer(config.kafkaSignalProducer.topic, config.kafka)

    val cpaClient = CpaRepoClient(defaultHttpClient())
    val validator = DokumentValidator(cpaClient)

    val processingClient = PayloadProcessingClient(scopedAuthHttpClient(EBMS_PAYLOAD_SCOPE))
    val processing = ProcessingService(processingClient)

    val sendInClient = SendInClient(scopedAuthHttpClient(EBMS_SEND_IN_SCOPE))
    val sendInService = SendInService(sendInClient)

    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    installMicrometerRegistry(appMicrometerRegistry)
    installRequestTimerPlugin()
    installContentNegotiation()

    routing {
        registerRootEndpoint()
        registerHealthEndpoints()
        registerPrometheusEndpoint(appMicrometerRegistry)
        registerNavCheckStatus()

        postEbmsAsync(validator, processing, ebmsMessageDetailsRepository, ebmsSignalProducer)
        postEbmsSync(validator, processing, sendInService, ebmsMessageDetailsRepository)
    }
}
