package no.nav.emottak.ediadapter.server.plugin

import io.github.smiley4.ktoropenapi.OpenApi
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
        /**
         OpenApiPluginConfig.examples {
         ExampleConfig.encoder(ExampleEncoder.kotlinx(json))
         }
         OpenApiPluginConfig.info {
         InfoConfig.title = "EDI 2.0 Adapter API"
         InfoConfig.version = "1.0.0"
         InfoConfig.description = "Wrapper for EDI 2.0 Messages API"
         }
         OpenApiPluginConfig.pathFilter = { _, url -> url.firstOrNull() == "api" }
         OpenApiPluginConfig.outputFormat = JSON
         */
    }
}
