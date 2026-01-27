package no.nav.emottak.ediadapter.server

import io.ktor.http.ContentType.Text.Plain
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.content.TextContent

sealed interface MessageError
sealed interface ValidationError : MessageError

data object MessageIdEmpty : ValidationError
data object MessageIdsMissing : ValidationError
data object ReceiverHerIdsMissing : ValidationError
data object MessageIdsEmpty : ValidationError
data object ReceiverHerIdsEmpty : ValidationError
data object HerIdEmpty : ValidationError
data object SenderHerIdMissing : ValidationError
data object SenderHerIdEmpty : ValidationError
data object BusinessDocumentIdEmpty : ValidationError
data object IncludeMetadataInvalidFormat : ValidationError
data object MessagesToFetchInvalidFormat : ValidationError
data object OrderByInvalidFormat : ValidationError

fun MessageError.toContent(): TextContent =
    when (this) {
        is MessageIdEmpty ->
            TextContent("Message id is empty")

        is MessageIdsMissing ->
            TextContent("Message ids are missing")

        is ReceiverHerIdsMissing ->
            TextContent("Receiver her ids are missing")

        is MessageIdsEmpty ->
            TextContent("Message ids are empty")

        is ReceiverHerIdsEmpty ->
            TextContent("Receiver her ids are empty")

        is HerIdEmpty ->
            TextContent("Her id is empty")

        is SenderHerIdMissing ->
            TextContent("Sender her id is missing")

        is SenderHerIdEmpty ->
            TextContent("Sender her id is empty")

        is BusinessDocumentIdEmpty ->
            TextContent("Business document id is empty")

        is IncludeMetadataInvalidFormat ->
            TextContent("Include metadata must be 'true' or 'false'")

        is MessagesToFetchInvalidFormat ->
            TextContent("Messages to fetch must be a number between 1 and 100")

        is OrderByInvalidFormat ->
            TextContent("Order by must be 1 (Ascending) or 2 (Descending)")
    }

private fun TextContent(
    content: String,
    statusCode: HttpStatusCode = BadRequest
): TextContent = TextContent(content, Plain, statusCode)
