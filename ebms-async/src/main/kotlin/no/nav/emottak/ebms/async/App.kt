/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package no.nav.emottak.ebms.async

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.core.raise.result
import arrow.fx.coroutines.resourceScope
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.CancellationException
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import no.nav.emottak.ebms.AZURE_AD_AUTH
import no.nav.emottak.ebms.CpaRepoClient
import no.nav.emottak.ebms.EBMS_PAYLOAD_SCOPE
import no.nav.emottak.ebms.EBMS_SEND_IN_SCOPE
import no.nav.emottak.ebms.PayloadProcessingClient
import no.nav.emottak.ebms.SMTP_TRANSPORT_SCOPE
import no.nav.emottak.ebms.SendInClient
import no.nav.emottak.ebms.SmtpTransportClient
import no.nav.emottak.ebms.async.configuration.Config
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.consumer.failedMessageQueue
import no.nav.emottak.ebms.async.kafka.consumer.getRecord
import no.nav.emottak.ebms.async.kafka.consumer.startPayloadReceiver
import no.nav.emottak.ebms.async.kafka.consumer.startSignalReceiver
import no.nav.emottak.ebms.async.kafka.producer.EbmsMessageProducer
import no.nav.emottak.ebms.async.persistence.Database
import no.nav.emottak.ebms.async.persistence.ebmsDbConfig
import no.nav.emottak.ebms.async.persistence.ebmsMigrationConfig
import no.nav.emottak.ebms.async.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.ebms.async.persistence.repository.PayloadRepository
import no.nav.emottak.ebms.async.processing.PayloadMessageForwardingService
import no.nav.emottak.ebms.async.processing.PayloadMessageService
import no.nav.emottak.ebms.async.processing.SignalMessageService
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.async.util.EventRegistrationServiceImpl
import no.nav.emottak.ebms.defaultHttpClient
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.registerHealthEndpoints
import no.nav.emottak.ebms.registerNavCheckStatus
import no.nav.emottak.ebms.registerPrometheusEndpoint
import no.nav.emottak.ebms.registerRootEndpoint
import no.nav.emottak.ebms.scopedAuthHttpClient
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.utils.environment.isProdEnv
import no.nav.emottak.utils.kafka.client.EventPublisherClient
import no.nav.emottak.utils.kafka.service.EventLoggingService
import org.slf4j.LoggerFactory

val log = LoggerFactory.getLogger("no.nav.emottak.ebms.async.App")

fun main() = SuspendApp {
    val database = Database(ebmsDbConfig.value)
    database.migrate(ebmsMigrationConfig.value)

    val config = config()
    val payloadRepository = PayloadRepository(database)
    val ebmsMessageDetailsRepository = EbmsMessageDetailsRepository(database)
    val processingClient = PayloadProcessingClient(scopedAuthHttpClient(EBMS_PAYLOAD_SCOPE))
    val processingService = ProcessingService(processingClient)
    val ebmsSignalProducer = EbmsMessageProducer(config.kafkaSignalProducer.topic, config.kafka)
    val ebmsPayloadProducer = EbmsMessageProducer(config.kafkaPayloadProducer.topic, config.kafka)

    val cpaClient = CpaRepoClient(defaultHttpClient())
    val cpaValidationService = CPAValidationService(cpaClient)

    val sendInClient = SendInClient(scopedAuthHttpClient(EBMS_SEND_IN_SCOPE))
    val sendInService = SendInService(sendInClient)

    val smtpTransportClient = SmtpTransportClient(scopedAuthHttpClient(SMTP_TRANSPORT_SCOPE))

    val kafkaPublisherClient = EventPublisherClient(config().kafka)
    val eventLoggingService = EventLoggingService(config().eventLogging, kafkaPublisherClient)
    val eventRegistrationService = EventRegistrationServiceImpl(eventLoggingService)

    val payloadMessageForwardingService = PayloadMessageForwardingService(
        sendInService = sendInService,
        cpaValidationService = cpaValidationService,
        processingService = processingService,
        payloadRepository = payloadRepository,
        ebmsMessageDetailsRepository = ebmsMessageDetailsRepository,
        ebmsPayloadProducer = ebmsPayloadProducer,
        eventRegistrationService = eventRegistrationService
    )

    val payloadMessageServiceProvider = payloadMessageServiceProvider(
        ebmsMessageDetailsRepository = ebmsMessageDetailsRepository,
        cpaValidationService = cpaValidationService,
        processingService = processingService,
        ebmsSignalProducer = ebmsSignalProducer,
        smtpTransportClient = smtpTransportClient,
        payloadMessageForwardingService = payloadMessageForwardingService,
        eventRegistrationService = eventRegistrationService
    )

    result {
        resourceScope {
            launchSignalReceiver(
                config = config,
                cpaValidationService = cpaValidationService,
                ebmsMessageDetailsRepository = ebmsMessageDetailsRepository
            )
            launchPayloadReceiver(
                config = config,
                payloadMessageServiceProvider = payloadMessageServiceProvider
            )

            server(
                Netty,
                port = 8080,
                module = {
                    ebmsProviderModule(
                        payloadRepository = payloadRepository,
                        payloadMessageServiceProvider = payloadMessageServiceProvider,
                        eventRegistrationService = eventRegistrationService
                    )
                }
            ).also { it.engineConfig.maxChunkSize = 100000 }

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

fun payloadMessageServiceProvider(
    ebmsMessageDetailsRepository: EbmsMessageDetailsRepository,
    cpaValidationService: CPAValidationService,
    processingService: ProcessingService,
    ebmsSignalProducer: EbmsMessageProducer,
    smtpTransportClient: SmtpTransportClient,
    payloadMessageForwardingService: PayloadMessageForwardingService,
    eventRegistrationService: EventRegistrationService

): () -> PayloadMessageService = {
    PayloadMessageService(
        ebmsMessageDetailsRepository = ebmsMessageDetailsRepository,
        cpaValidationService = cpaValidationService,
        processingService = processingService,
        ebmsSignalProducer = ebmsSignalProducer,
        smtpTransportClient = smtpTransportClient,
        payloadMessageForwardingService = payloadMessageForwardingService,
        eventRegistrationService = eventRegistrationService
    )
}

private fun CoroutineScope.launchPayloadReceiver(
    config: Config,
    payloadMessageServiceProvider: () -> PayloadMessageService
) {
    if (config.kafkaPayloadReceiver.active) {
        launch(Dispatchers.IO) {
            startPayloadReceiver(
                config.kafkaPayloadReceiver.topic,
                config.kafka,
                payloadMessageServiceProvider.invoke()
            )
        }
    }
}

private fun CoroutineScope.launchSignalReceiver(
    config: Config,
    cpaValidationService: CPAValidationService,
    ebmsMessageDetailsRepository: EbmsMessageDetailsRepository
) {
    if (config.kafkaSignalReceiver.active) {
        launch(Dispatchers.IO) {
            val signalProcessor = SignalMessageService(
                ebmsMessageDetailsRepository,
                cpaValidationService
            )
            startSignalReceiver(config.kafkaSignalReceiver.topic, config.kafka, signalProcessor)
        }
    }
}

fun Application.ebmsProviderModule(
    payloadRepository: PayloadRepository,
    payloadMessageServiceProvider: () -> PayloadMessageService,
    eventRegistrationService: EventRegistrationService
) {
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    installMicrometerRegistry(appMicrometerRegistry)
    installContentNegotiation()
    installAuthentication()

    routing {
        registerRootEndpoint()
        registerHealthEndpoints()
        registerPrometheusEndpoint(appMicrometerRegistry)
        registerNavCheckStatus()
        if (!isProdEnv()) {
            simulateError()
        }
        retryErrors(payloadMessageServiceProvider)
        authenticate(AZURE_AD_AUTH) {
            getPayloads(payloadRepository, eventRegistrationService)
        }
    }
}

const val RETRY_LIMIT = "retryLimit"

fun Routing.retryErrors(
    payloadMessageServiceProvider: () -> PayloadMessageService
): Route =
    get("/api/retry/{$RETRY_LIMIT}") {
        if (!config().kafkaErrorQueue.active) {
            call.respondText(status = HttpStatusCode.ServiceUnavailable, text = "Retry not active.")
            return@get
        }
        failedMessageQueue.consumeRetryQueue(
            payloadMessageServiceProvider.invoke(),
            limit = (call.parameters[RETRY_LIMIT])?.toInt() ?: 10
        )
        call.respondText(
            status = HttpStatusCode.OK,
            text = "Retry processing started with limit ${call.parameters[RETRY_LIMIT] ?: "default"}"
        )
    }

const val KAFKA_OFFSET = "offset"

fun Route.simulateError(): Route = get("/api/forceretry/{$KAFKA_OFFSET}") {
    CoroutineScope(Dispatchers.IO).launch() {
        if (config().kafkaErrorQueue.active) {
            val record = getRecord(
                config()
                    .kafkaPayloadReceiver.topic,
                config().kafka
                    .copy(groupId = "ebms-provider-retry"),
                (call.parameters[KAFKA_OFFSET])?.toLong() ?: 0
            )
            failedMessageQueue.sendToRetry(
                record = record ?: throw Exception("No Record found. Offset: ${call.parameters[KAFKA_OFFSET]}"),
                reason = "Simulated Error"
            )
        }
    }
}
