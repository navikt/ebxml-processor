package no.nav.emottak.cpa

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
import no.nav.emottak.cpa.model.Certificate
import java.io.File

fun getFakeNhnAdresseregisterEngine(): MockEngine =
    MockEngine { request ->

        val responseBody = when {
            request.url.encodedPath.contains("/api/v1/communicationparty/79768") -> {
                File("src/main/resources/CommunicationParty.json").readText()
            }
            request.url.encodedPath.contains("/api/v1/certificate/1/signing") -> {
                val currentSigning = "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"
                var signCertificate = Certificate(
                    "EE5375B85C2ADD34831F3D4EDC900CACB267BB72",
                    "2024-09-26T13:31:53+00:00",
                    "2027-09-26T21:59:00+00:00",
                    currentSigning
                )
                // CommunicationParty(kotlin.collections.Collection<CommunicationPartyElement>)

                Json.encodeToString(signCertificate)
            }
            request.url.encodedPath.contains("/api/v1/certificate/1/encryption") -> {
                val encryptCertificate = "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"
                var signCertificate = Certificate(
                    "D2A7137640A4CE620410C53A7BEDE4D00A3FC611",
                    "2027-09-26T21:59:00+00:00",
                    "2028-11-01T23:23:59+00:00",
                    encryptCertificate
                )

                Json.encodeToString(signCertificate)
            }
            else -> """{"error": "not found"}"""
        }

        respond(
            content = ByteReadChannel(responseBody),
            status = if (responseBody.contains("error")) HttpStatusCode.NotFound else HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
