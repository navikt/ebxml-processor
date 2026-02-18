package no.nav.helsemelding.ediadapter.server.plugin

import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.ExampleEncoder
import io.github.smiley4.ktoropenapi.config.OutputFormat.JSON
import io.ktor.server.application.Application
import io.ktor.server.application.install
import kotlinx.serialization.json.Json

fun Application.configureOpenApi() {
    install(OpenApi) {
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = false
        }
        examples {
            encoder(ExampleEncoder.kotlinx(json))
        }
        info {
            title = "EDI 2.0 Adapter API"
            version = "1.0.0"
            description = "Wrapper for EDI 2.0 Messages API"
        }
        pathFilter = { _, url -> url.firstOrNull() == "api" }
        outputFormat = JSON
    }
}
