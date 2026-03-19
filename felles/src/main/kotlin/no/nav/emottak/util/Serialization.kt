package no.nav.emottak.util

import io.ktor.http.ContentType
import io.ktor.serialization.Configuration
import io.ktor.serialization.kotlinx.serialization
import kotlinx.serialization.json.Json

val LENIENT_JSON_PARSER = Json {
    encodeDefaults = true
    isLenient = true
    allowSpecialFloatingPointValues = true
    allowStructuredMapKeys = true
    prettyPrint = false
    useArrayPolymorphism = false
    ignoreUnknownKeys = true
}

fun Configuration.jsonLenient(
    json: Json = LENIENT_JSON_PARSER,
    contentType: ContentType = ContentType.Application.Json
) {
    serialization(contentType, json)
}
