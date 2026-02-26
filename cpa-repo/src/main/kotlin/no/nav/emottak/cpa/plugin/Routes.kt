package no.nav.emottak.cpa.plugin

import arrow.core.raise.recover
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.emottak.cpa.HeridError
import no.nav.emottak.cpa.configuration.config
import no.nav.emottak.cpa.herId
import no.nav.emottak.cpa.plugin.MessagesApi.GET_HERID
import no.nav.emottak.cpa.plugin.MessagesApi.getHerIdDocs
import no.nav.emottak.cpa.toContent

private val log = KotlinLogging.logger { }

fun Application.configureRoutes(
    ediClient: HttpClient,
    registry: PrometheusMeterRegistry
) {
    routing {
        // helloWorld()
        swaggerRoutes()
        internalRoutes(registry)

        authenticate(config().nhnConfig.azureAuth.issuer.value) {
            externalRoutes(ediClient)
        }
    }
}

fun Route.swaggerRoutes() {
    route("api.json") {
        openApi()
    }
    route("swagger") {
        swaggerUI("/api.json") {
        }
    }
}

// fun Route.helloWorld() = get("/hello") {
//    call.respondText("Hello World")
// }

fun Route.internalRoutes(registry: PrometheusMeterRegistry) {
    get("/prometheus") {
        call.respond(registry.scrape())
    }
    route("/internal") {
        get("/health/liveness") {
            call.respondText("I'm alive! :)")
        }
        get("/health/readiness") {
            call.respondText("I'm ready! :)")
        }
    }
}

fun Route.externalRoutes(ediClient: HttpClient) {
    route("/api/v1") {
        get(GET_HERID, getHerIdDocs) {
            recover(
                {
                    // val params = call.pathParameters["herId"]
                    val params = herId(call)
                    val response = ediClient.get("/api/v1/CommunicationParties/$params")
                    call.respondText(
                        text = response.bodyAsText(),
                        contentType = Json,
                        status = response.status
                    )
                },
                { e: HeridError -> call.respond(e.toContent()) }
            ) { t: Throwable -> call.respondInternalError(t) }
        }
    }
}

private suspend fun ApplicationCall.respondInternalError(t: Throwable) {
    log.error(t) { "Unexpected error while processing request" + t.message }
    respond(InternalServerError)
}
