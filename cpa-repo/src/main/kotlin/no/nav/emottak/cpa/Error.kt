package no.nav.emottak.cpa

import io.ktor.http.ContentType.Text.Plain
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.content.TextContent

sealed interface HeridError
sealed interface ValidationError : HeridError
data object HerIdEmpty : ValidationError
data object HerIdMissing : ValidationError
data object BusinessDocumentIdEmpty : ValidationError
data object IncludeMetadataInvalidFormat : ValidationError
data object OrderByInvalidFormat : ValidationError

fun HeridError.toContent(): TextContent =
    when (this) {
        is HerIdEmpty ->
            TextContent("Her id is empty")

        is HerIdMissing ->
            TextContent("Her id is missing")

        is BusinessDocumentIdEmpty ->
            TextContent("Business document id is empty")

        is IncludeMetadataInvalidFormat ->
            TextContent("Include metadata must be 'true' or 'false'")

        is OrderByInvalidFormat ->
            TextContent("Order by must be 1 (Ascending) or 2 (Descending)")
    }

private fun TextContent(
    content: String,
    statusCode: HttpStatusCode = BadRequest
): TextContent = TextContent(content, Plain, statusCode)
