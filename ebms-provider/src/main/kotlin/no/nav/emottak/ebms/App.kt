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
import io.ktor.utils.io.core.toByteArray
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import net.logstash.logback.marker.Markers
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.ebms.configuration.config
import no.nav.emottak.ebms.messaging.EbmsSignalProducer
import no.nav.emottak.ebms.messaging.startSignalReceiver
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
import java.util.UUID
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
    if (config.kafkaSignalReceiver.active) {
        launch(Dispatchers.IO) {
            startSignalReceiver(config.kafkaSignalReceiver.topic, config.kafka)
        }
    }
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
    dbConfig: HikariConfig,
    migrationConfig: HikariConfig
) {
    val config = config()

    val database = Database(dbConfig)
    database.migrate(migrationConfig)

    val ebmsMessageRepository = EbmsMessageRepository(database)

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

    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/") {
            call.respondText("Hello, world!")
        }

        get("/kafkatest_write") {
            log.debug("Kafka test write: start")

            val key = UUID.randomUUID().toString()
            val value = """<SOAP:Envelope xmlns:eb="http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:SOAP="http://schemas.xmlsoap.org/soap/envelope/" xsi:schemaLocation="http://schemas.xmlsoap.org/soap/envelope/ http://www.oasis-open.org/committees/ebxml-msg/schema/envelope.xsd http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd"><SOAP:Header><eb:MessageHeader xmlns:SOAP="http://schemas.xmlsoap.org/soap/envelope/" eb:version="2.0" SOAP:mustUnderstand="1" xmlns:eb="http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd"><eb:From><eb:PartyId eb:type="HER">92173</eb:PartyId><eb:Role>Utleverer</eb:Role></eb:From><eb:To><eb:PartyId eb:type="HER">79768</eb:PartyId><eb:Role>KontrollUtbetaler</eb:Role></eb:To><eb:CPAId>nav:qass:33494</eb:CPAId><eb:ConversationId>cd7602af-6b02-4cb2-bd07-bc5d39110239</eb:ConversationId><eb:Service>urn:oasis:names:tc:ebxml-msg:service</eb:Service><eb:Action>Acknowledgment</eb:Action><eb:MessageData><eb:MessageId>87ad32cd-1725-4290-91e5-5dd952078064</eb:MessageId><eb:Timestamp>2025-01-21T11:31:55.2201617Z</eb:Timestamp><eb:RefToMessageId>20250121-123105-93382@qa.ebxml.nav.no</eb:RefToMessageId></eb:MessageData></eb:MessageHeader><eb:Acknowledgment xmlns:SOAP="http://schemas.xmlsoap.org/soap/envelope/" eb:version="2.0" SOAP:mustUnderstand="true" SOAP:actor="urn:oasis:names:tc:ebxml-msg:actor:toPartyMSH" xmlns:eb="http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd"><eb:Timestamp>2025-01-21T11:31:05Z</eb:Timestamp><eb:RefToMessageId>20250121-123105-93382@qa.ebxml.nav.no</eb:RefToMessageId><Reference URI="" xmlns="http://www.w3.org/2000/09/xmldsig#"><Transforms><Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature"></Transform><Transform Algorithm="http://www.w3.org/TR/1999/REC-xpath-19991116"><XPath xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">not(ancestor-or-self::node()[@SOAP-ENV:actor="urn:oasis:names:tc:ebxml-msg:actor:nextMSH"] | ancestor-or-self::node()[@SOAP-ENV:actor="http://schemas.xmlsoap.org/soap/actor/next"])</XPath></Transform><Transform Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"></Transform></Transforms><DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"></DigestMethod><DigestValue>GX4UBbZiFlwQJZbl9H83OwnxEvlpMafgD06jvqIWUGM=</DigestValue></Reference></eb:Acknowledgment><Signature xmlns="http://www.w3.org/2000/09/xmldsig#"><SignedInfo><CanonicalizationMethod Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315" /><SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256" /><Reference URI=""><Transforms><Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature" /><Transform Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315" /></Transforms><DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256" /><DigestValue>B6KfRMC8Z4t1lhYUOaqE/h0xMea/0tyC6Z10IVXbOvo=</DigestValue></Reference></SignedInfo><SignatureValue>CUs03AwWn0+nU5+bZvTEMZrtNRAZ5wEBsCBnjTnNyoxlxLq6Pu0k5ruiEOrGZumM/XqpkAwr9cbA5WM4MzV8IYKcNJ2/u6aaYC+Tc/51buXXe1Tqxi5ZIjSRvOyXllD5aUk3EcCg/IPHHuU9cKt8ZLKep7/HqYqf+2mCxZ4fS8Gq8ZJKVflPCVubMezkL2uLL6X+t4xWsi7490m9UYr3l4gmU38a+8pdfla/1lDy3MXXck/UKEgqDEDX6aOPMwNlcc5/+SX1nGEk6GBSKjkrBKuF/kuwT+9yc0pW5eApkc1QyZV402uxcs83vXZxX8zCKlRfEUuP454W+8oEDcwOSoetMv81t5jDXS5CxHWszICqLBdLAwf94Qgv8IoIeUrpbGtEZx6VizEnsU/GUtTPXgSYmIPFIILhpTAwcXdw8I+eH4G+pgYqnPTe/N1ZNhQU30Z/5Z31XIvv343kzWxX5B+yvivTZjyZlPQIvG7lDUQjeOqWdFXnyi+aPCnNS33f</SignatureValue><KeyInfo><X509Data><X509Certificate>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</X509Certificate></X509Data><KeyValue><RSAKeyValue><Modulus>kp24TFsB/vYW+PbVqYCZBOBwc9CX14O6yh7y/u9zeHRi1boeab4WiZD+XUt1zG3tre25Z6bnlOTW/MijqA6UjpQTyFY6vzCoyAgJU0qrXlEnOgnsSzbS8FjWMRopYjhiiYh7LGS9v7B7sAbXGKgzutbB3vlHjZWQs6O8NBjDJZyduPXg11+/XSZOaJJoioMBlDUrFLYRiixlJYrkEAXBSjZ1SG2L0HcYkR/aNuIFG/cUdMpCSeFcfNjdOcu5kap5sj0a6l5b6IYOFJKijBXBstFWT0aAiAgIZIzKh9G/IQSkgjEDImXzYQO73wD38mINeghcnt4IQWSH/qnO022AbriMnvUQ9+gCo/lmBBcCCpq1CFNtCeZTCWTNAVAbmEV3dVkrhC4nFWiiFoYg62Fvazplh9FPUW23cVarglGi+EnYTbzl4IcNjqxBmSluBYsWr3tM2bYyCFCjD+8A+vkG2wyJXrz8zOOJ4E00SDRy3HHRxLpcDJeDdrDmUrtMzhGp</Modulus><Exponent>AQAB</Exponent></RSAKeyValue></KeyValue></KeyInfo></Signature></SOAP:Header><SOAP:Body /></SOAP:Envelope>"""

            ebmsSignalProducer.send(key, value.toByteArray())

            try {
                ebmsSignalProducer.send(key, value.toByteArray())
            } catch (e: Exception) {
                log.error("Kafka test write: Exception while reading messages from queue", e)
            }
            log.debug("Kafka test write: done")

            call.respondText("Kafka works!")
        }

        if (!isProdEnv()) {
            packageEbxml(validator, processing)
            unpackageEbxml(validator, processing)
        }
        registerHealthEndpoints(appMicrometerRegistry)
        navCheckStatus()
        postEbmsAsync(validator, processing, ebmsMessageRepository, ebmsSignalProducer)
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
