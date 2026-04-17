package no.nav.emottak.util

import io.ktor.http.ContentType
import io.ktor.serialization.Configuration
import io.ktor.serialization.kotlinx.serialization
import kotlinx.serialization.json.Json
import no.nav.emottak.utils.serialization.LENIENT_JSON_PARSER

fun Configuration.jsonLenient(
    json: Json = LENIENT_JSON_PARSER,
    contentType: ContentType = ContentType.Application.Json
) {
    serialization(contentType, json)
}
