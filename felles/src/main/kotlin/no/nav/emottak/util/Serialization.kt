package no.nav.emottak.util

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
