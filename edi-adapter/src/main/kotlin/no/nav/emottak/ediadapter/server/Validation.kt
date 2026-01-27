package no.nav.emottak.ediadapter.server

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.ktor.server.application.ApplicationCall

private const val MESSAGE_ID = "messageId"
private const val HER_ID = "herId"
private const val APP_REC_SENDER_HER_ID = "apprecSenderHerId"
private const val SENDER_HER_ID = "senderHerId"
private const val RECEIVER_HER_IDS = "receiverHerIds"
private const val BUSINESS_DOCUMENT_ID = "businessDocumentId"
private const val INCLUDE_METADATA = "includeMetadata"
private const val MESSAGES_TO_FETCH = "messagesToFetch"
private const val ORDER_BY = "orderBy"

fun Raise<ValidationError>.messageId(call: ApplicationCall): String =
    requiredPathParam(call, MESSAGE_ID, MessageIdEmpty)

fun Raise<ValidationError>.herId(call: ApplicationCall): String =
    requiredPathParam(call, HER_ID, HerIdEmpty)

fun Raise<ValidationError>.apprecSenderHerId(call: ApplicationCall): String =
    requiredPathParam(call, APP_REC_SENDER_HER_ID, SenderHerIdEmpty)

fun Raise<ValidationError>.receiverHerIds(call: ApplicationCall): List<String> =
    ensureNotNull(call.request.queryParameters.getAll(RECEIVER_HER_IDS)) { ReceiverHerIdsMissing }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .also { ensure(it.isNotEmpty()) { ReceiverHerIdsEmpty } }

fun Raise<ValidationError>.senderHerId(call: ApplicationCall): String? =
    optionalNonBlankQueryParam(call, SENDER_HER_ID, SenderHerIdEmpty)

fun Raise<ValidationError>.businessDocumentId(call: ApplicationCall): String? =
    optionalNonBlankQueryParam(call, BUSINESS_DOCUMENT_ID, BusinessDocumentIdEmpty)

fun Raise<ValidationError>.includeMetadata(call: ApplicationCall): Boolean? =
    call.request.queryParameters[INCLUDE_METADATA]
        ?.trim()
        ?.also { ensure(it.isNotEmpty()) { IncludeMetadataInvalidFormat } }
        ?.let { it.toBooleanStrictOrNull() ?: raise(IncludeMetadataInvalidFormat) }

fun Raise<ValidationError>.messagesToFetch(call: ApplicationCall): Int? =
    call.request.queryParameters[MESSAGES_TO_FETCH]
        ?.trim()
        ?.also { ensure(it.isNotEmpty()) { MessagesToFetchInvalidFormat } }
        ?.toIntOrNull()
        ?.also { ensure(it in 1..100) { MessagesToFetchInvalidFormat } }

fun Raise<ValidationError>.orderBy(call: ApplicationCall): Int? =
    call.request.queryParameters[ORDER_BY]
        ?.trim()
        ?.also { ensure(it.isNotEmpty()) { OrderByInvalidFormat } }
        ?.toIntOrNull()
        ?.also { ensure(it == 1 || it == 2) { OrderByInvalidFormat } }

private fun Raise<ValidationError>.requiredPathParam(
    call: ApplicationCall,
    name: String,
    emptyError: ValidationError
): String =
    ensureNotNull(call.parameters[name]) { emptyError }
        .also { ensure(it.isNotBlank()) { emptyError } }

private fun Raise<ValidationError>.optionalNonBlankQueryParam(
    call: ApplicationCall,
    name: String,
    emptyError: ValidationError
): String? =
    call.request.queryParameters[name]
        ?.trim()
        ?.also { ensure(it.isNotEmpty()) { emptyError } }
