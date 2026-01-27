package no.nav.emottak.ediadapter.server

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.core.raise.result
import arrow.fx.coroutines.resourceScope
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.engine.logError
import io.ktor.server.netty.Netty
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import no.nav.emottak.ediadapter.server.plugin.configureAuthentication
import no.nav.emottak.ediadapter.server.plugin.configureCallLogging
import no.nav.emottak.ediadapter.server.plugin.configureContentNegotiation
import no.nav.emottak.ediadapter.server.plugin.configureMetrics
import no.nav.emottak.ediadapter.server.plugin.configureOpenApi
import no.nav.emottak.ediadapter.server.plugin.configureRoutes

private val log = KotlinLogging.logger {}

fun main() = SuspendApp {
    result {
        resourceScope {
            log.info { "KIRAMMMMMMMMMMMMMMMMMMMMMMMM starting" }
            val deps = dependencies()
            log.info { "KIRAMMMMMMMMMMMMMMMMMMMMMMMM depenendcies are read" }

            server(
                Netty,
                port = config().server.port.value,
                preWait = config().server.preWait,
                module = ediAdapterModule(deps.httpClient, deps.meterRegistry)
            )
            log.info { "KIRAMMMMMMMMMMMMMMMMMMMMMMMM $config().server.port.value   $config().server.preWait" }
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

private fun logError(t: Throwable) = log.error { "Shutdown edi-adapter due to: ${t.stackTraceToString()}" }
