package no.nav.emottak.cpa

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.core.raise.result
import arrow.fx.coroutines.resourceScope
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.netty.Netty
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import no.nav.emottak.cpa.plugin.configureAuthentication
import no.nav.emottak.cpa.plugin.configureCallLogging
import no.nav.emottak.cpa.plugin.configureContentNegotiation
import no.nav.emottak.cpa.plugin.configureMetrics
import no.nav.emottak.cpa.plugin.configureOpenApi
import no.nav.emottak.cpa.plugin.configureRoutes

private val logs = KotlinLogging.logger {}

fun main() = SuspendApp {
    // miljø https://cpapi.test.grunndata.nhn.no
    // Autentisering metricsRegistry
    // nhn:communicationparty/read
    // GET /Certificate/{herId}/History
    result {
        resourceScope {
            logs.info { "Starting server............." }
            val deps = dependencies()

            logs.info { " Dependencies are read........" }
            server(
                Netty,
                port = config().server.port.value,
                preWait = config().server.preWait,
                module = ediAdapterModule(deps.httpClient, deps.meterRegistry)
            )

            awaitCancellation()
        }
    }
        .onFailure { error -> if (error !is CancellationException) logError(error) }
}

internal fun ediAdapterModule(
    ediClient: HttpClient,
    meterRegistry: PrometheusMeterRegistry
): Application.() -> Unit {
    return {
        configureMetrics(meterRegistry)
        configureContentNegotiation()
        configureAuthentication()
        configureRoutes(ediClient, meterRegistry)
        configureCallLogging()
        configureOpenApi()
    }
}

private fun logError(t: Throwable) = logs.error { "Shutdown edi-adapter due to: ${t.stackTraceToString()}" }
