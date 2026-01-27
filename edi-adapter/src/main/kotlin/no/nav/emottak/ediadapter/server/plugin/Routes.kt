package no.nav.emottak.ediadapter.server.plugin

import arrow.core.raise.Raise
import arrow.core.raise.recover
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Location
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.serialization.encodeToString
import no.nav.emottak.ediadapter.server.MessageError
import no.nav.emottak.ediadapter.server.ValidationError
import no.nav.emottak.ediadapter.server.apprecSenderHerId
import no.nav.emottak.ediadapter.server.businessDocumentId
import no.nav.emottak.ediadapter.server.config
import no.nav.emottak.ediadapter.server.herId
import no.nav.emottak.ediadapter.server.includeMetadata
import no.nav.emottak.ediadapter.server.messageId
import no.nav.emottak.ediadapter.server.messagesToFetch
import no.nav.emottak.ediadapter.server.orderBy
import no.nav.emottak.ediadapter.server.plugin.MessagesApi.GET_APPREC
import no.nav.emottak.ediadapter.server.plugin.MessagesApi.GET_DOCUMENT
import no.nav.emottak.ediadapter.server.plugin.MessagesApi.GET_MESSAGE
import no.nav.emottak.ediadapter.server.plugin.MessagesApi.GET_MESSAGES
import no.nav.emottak.ediadapter.server.plugin.MessagesApi.GET_STATUS
import no.nav.emottak.ediadapter.server.plugin.MessagesApi.MARK_READ
import no.nav.emottak.ediadapter.server.plugin.MessagesApi.POST_APPREC
import no.nav.emottak.ediadapter.server.plugin.MessagesApi.POST_MESSAGE
import no.nav.emottak.ediadapter.server.plugin.MessagesApi.getApprecDocs
import no.nav.emottak.ediadapter.server.plugin.MessagesApi.getDocumentDocs
import no.nav.emottak.ediadapter.server.plugin.MessagesApi.getMessageDocs
import no.nav.emottak.ediadapter.server.plugin.MessagesApi.getMessagesDocs
import no.nav.emottak.ediadapter.server.plugin.MessagesApi.getStatusDocs
import no.nav.emottak.ediadapter.server.plugin.MessagesApi.markReadDocs
import no.nav.emottak.ediadapter.server.plugin.MessagesApi.postApprecDocs
import no.nav.emottak.ediadapter.server.plugin.MessagesApi.postMessageDocs
import no.nav.emottak.ediadapter.server.receiverHerIds
import no.nav.emottak.ediadapter.server.senderHerId
import no.nav.emottak.ediadapter.server.toContent
import no.nav.helsemelding.ediadapter.model.Metadata
import no.nav.helsemelding.ediadapter.model.PostAppRecRequest
import no.nav.helsemelding.ediadapter.model.PostMessageRequest
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json as JsonUtil

private val log = KotlinLogging.logger { }

private const val RECEIVER_HER_IDS = "ReceiverHerIds"
private const val SENDER_HER_ID = "SenderHerId"
private const val BUSINESS_DOCUMENT_ID = "BusinessDocumentId"
private const val INCLUDE_METADATA = "IncludeMetadata"
private const val MESSAGES_TO_FETCH = "MessagesToFetch"
private const val ORDER_BY = "OrderBy"

fun Application.configureRoutes(
    ediClient: HttpClient,
    registry: PrometheusMeterRegistry
) {
    routing {
        swaggerRoutes()
        internalRoutes(registry)

        authenticate(config().azureAuth.issuer.value) {
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
        get(GET_MESSAGES, getMessagesDocs) {
            recover(
                {
                    val params = messageQueryParams(call)
                    val response = ediClient.get("Messages") { url { parameters.appendAll(params) } }
                    call.respondText(
                        text = response.bodyAsText(),
                        contentType = Json,
                        status = response.status
                    )
                },
                { e: MessageError -> call.respond(e.toContent()) }
            ) { t: Throwable -> call.respondInternalError(t) }
        }

        get(GET_MESSAGE, getMessageDocs) {
            recover(
                {
                    val messageId = messageId(call)
                    val response = ediClient.get("Messages/$messageId")
                    call.respondText(
                        text = response.bodyAsText(),
                        contentType = Json,
                        status = response.status
                    )
                },
                { e: MessageError -> call.respond(e.toContent()) }
            ) { t: Throwable -> call.respondInternalError(t) }
        }

        get(GET_DOCUMENT, getDocumentDocs) {
            recover(
                {
                    val messageId = messageId(call)
                    val response = ediClient.get("Messages/$messageId/business-document")
                    call.respondText(
                        text = response.bodyAsText(),
                        contentType = Json,
                        status = response.status
                    )
                },
                { e: MessageError -> call.respond(e.toContent()) }
            ) { t: Throwable -> call.respondInternalError(t) }
        }

        get(GET_STATUS, getStatusDocs) {
            recover(
                {
                    val messageId = messageId(call)
                    val response = ediClient.get("Messages/$messageId/status")
                    call.respondText(
                        text = response.bodyAsText(),
                        contentType = Json,
                        status = response.status
                    )
                },
                { e: MessageError -> call.respond(e.toContent()) }
            ) { t: Throwable -> call.respondInternalError(t) }
        }

        get(GET_APPREC, getApprecDocs) {
            recover(
                {
                    val messageId = messageId(call)
                    val response = ediClient.get("Messages/$messageId/apprec")
                    call.respondText(
                        text = response.bodyAsText(),
                        contentType = Json,
                        status = response.status
                    )
                },
                { e: MessageError -> call.respond(e.toContent()) }
            ) { t: Throwable -> call.respondInternalError(t) }
        }

        post(POST_MESSAGE, postMessageDocs) {
            val message = call.receive<PostMessageRequest>()
            recover(
                {
                    val response = ediClient.post("Messages") {
                        contentType(Json)
                        setBody(message)
                    }
                    call.respondText(
                        text = response.toMetadata(),
                        contentType = Json,
                        status = response.status
                    )
                },
                { e: MessageError -> call.respond(e.toContent()) }
            ) { t: Throwable -> call.respondInternalError(t) }
        }

        post(POST_APPREC, postApprecDocs) {
            val appRec = call.receive<PostAppRecRequest>()
            recover(
                {
                    val messageId = messageId(call)
                    val senderHerId = apprecSenderHerId(call)

                    val response = ediClient.post("Messages/$messageId/apprec/$senderHerId") {
                        contentType(Json)
                        setBody(appRec)
                    }
                    call.respondText(
                        text = response.toMetadata(),
                        contentType = Json,
                        status = response.status
                    )
                },
                { e: MessageError -> call.respond(e.toContent()) }
            ) { t: Throwable -> call.respondInternalError(t) }
        }

        put(MARK_READ, markReadDocs) {
            recover(
                {
                    val messageId = messageId(call)
                    val herId = herId(call)
                    val response = ediClient.put("Messages/$messageId/read/$herId")
                    call.respondText(
                        text = response.bodyAsText(),
                        contentType = Json,
                        status = response.status
                    )
                },
                { e: MessageError -> call.respond(e.toContent()) }
            ) { t: Throwable -> call.respondInternalError(t) }
        }
    }
}

private suspend fun HttpResponse.toMetadata(): String {
    val body = bodyAsText()
    val location = headers[Location] ?: return body

    val id = JsonUtil.decodeFromString<Uuid>(body)

    val metadata = Metadata(
        id = id,
        location = location
    )

    return JsonUtil.encodeToString(metadata)
}

private fun Raise<ValidationError>.messageQueryParams(
    call: ApplicationCall
): Parameters {
    val receiverHerIds = receiverHerIds(call)
    val senderHerId = senderHerId(call)
    val businessDocumentId = businessDocumentId(call)
    val includeMetadata = includeMetadata(call)
    val messagesToFetch = messagesToFetch(call)
    val orderBy = orderBy(call)

    return Parameters.build {
        appendAll(RECEIVER_HER_IDS, receiverHerIds)
        appendIfPresent(SENDER_HER_ID, senderHerId)
        appendIfPresent(BUSINESS_DOCUMENT_ID, businessDocumentId)
        appendIfPresent(INCLUDE_METADATA, includeMetadata)
        appendIfPresent(MESSAGES_TO_FETCH, messagesToFetch)
        appendIfPresent(ORDER_BY, orderBy)
    }
}

private fun ParametersBuilder.appendIfPresent(name: String, value: Any?) =
    value?.let { append(name, it.toString()) }

private suspend fun ApplicationCall.respondInternalError(t: Throwable) {
    log.error(t) { "Unexpected error while processing request" }
    respond(InternalServerError)
}
