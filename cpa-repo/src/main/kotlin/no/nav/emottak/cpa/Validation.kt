package no.nav.emottak.cpa

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.ktor.server.application.ApplicationCall

private const val HER_ID = "herId"
private const val BUSINESS_DOCUMENT_ID = "businessDocumentId"
private const val INCLUDE_METADATA = "includeMetadata"
private const val ORDER_BY = "orderBy"

fun Raise<ValidationError>.herId(call: ApplicationCall): String =
    requiredPathParam(call, HER_ID, HerIdEmpty)

fun Raise<ValidationError>.businessDocumentId(call: ApplicationCall): String? =
    optionalNonBlankQueryParam(call, BUSINESS_DOCUMENT_ID, BusinessDocumentIdEmpty)

fun Raise<ValidationError>.includeMetadata(call: ApplicationCall): Boolean? =
    call.request.queryParameters[INCLUDE_METADATA]
        ?.trim()
        ?.also { ensure(it.isNotEmpty()) { IncludeMetadataInvalidFormat } }
        ?.let { it.toBooleanStrictOrNull() ?: raise(IncludeMetadataInvalidFormat) }

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
