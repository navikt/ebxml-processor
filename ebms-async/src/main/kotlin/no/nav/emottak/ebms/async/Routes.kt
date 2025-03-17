package no.nav.emottak.ebms.async

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.emottak.ebms.async.persistence.repository.PayloadRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val REFERENCE_ID = "referenceId"

@OptIn(ExperimentalUuidApi::class)
fun Route.getPayloads(
    payloadRepository: PayloadRepository
): Route = get("/api/payloads/{$REFERENCE_ID}") {
    var referenceIdParameter: String? = null
    val referenceId: Uuid?
    // Validation
    try {
        referenceIdParameter = call.parameters[REFERENCE_ID]
        referenceId = Uuid.parse(referenceIdParameter!!)
    } catch (iae: IllegalArgumentException) {
        log.error("Invalid reference ID $referenceIdParameter has been sent", iae)
        call.respond(
            HttpStatusCode.BadRequest,
            iae.getErrorMessage()
        )
        return@get
    } catch (ex: Exception) {
        log.error("Exception occurred while validation of async payload request")
        call.respond(
            HttpStatusCode.BadRequest,
            ex.getErrorMessage()
        )
        return@get
    }

    // Sending response
    try {
        val listOfPayloads = payloadRepository.getByReferenceId(referenceId)

        if (listOfPayloads.isEmpty()) {
            call.respond(HttpStatusCode.NotFound, "Payload not found for reference ID $referenceId")
        } else {
            call.respond(HttpStatusCode.OK, listOfPayloads)
        }
    } catch (ex: Exception) {
        log.error("Exception occurred while retrieving Payload: ${ex.localizedMessage} (${ex::class.qualifiedName})")
        call.respond(
            HttpStatusCode.InternalServerError,
            ex.getErrorMessage()
        )
    }
    return@get
}

fun Exception.getErrorMessage(): String {
    return localizedMessage ?: cause?.message ?: javaClass.simpleName
}
