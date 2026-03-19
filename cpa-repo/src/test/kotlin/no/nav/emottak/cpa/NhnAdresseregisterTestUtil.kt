package no.nav.emottak.cpa

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import java.io.File

fun getFakeNhnAdresseregisterEngine(): MockEngine =
    MockEngine { request ->

        val responseBody = when {
            // Test nr 1 Speare
            request.url.encodedPath.contains("/api/v1/communicationparty/8141253") -> {
                File("src/test/resources/adresseregister/Speare_CommunicationParty.json").readText()
            }
            request.url.encodedPath.contains("/api/v1/certificate/8141253/signing") -> {
                File("src/test/resources/adresseregister/SPEAREAS_qass_35065_SignCert.json").readText()
            }
            request.url.encodedPath.contains("/api/v1/certificate/8141253/encryption") -> {
                File("src/test/resources/adresseregister/SPEAREAS_qass_35065_EncryptCert.json").readText()
                // Json.encodeToString(signCertificate)
            }
            // Test nr 2 Nav
            request.url.encodedPath.contains("/api/v1/communicationparty/79768") -> {
                File("src/test/resources/adresseregister/Speare_CommunicationParty.json").readText()
            }
            request.url.encodedPath.contains("/api/v1/certificate/79768/signing") -> {
                File("src/test/resources/adresseregister/SPEAREAS_qass_35065_SignCert.json").readText()
            }
            request.url.encodedPath.contains("/api/v1/certificate/79768/encryption") -> {
                File("src/test/resources/adresseregister/SPEAREAS_qass_35065_EncryptCert.json").readText()
                // Json.encodeToString(signCertificate)
            }
            else -> """{"error": "not found"}"""
        }

        respond(
            content = ByteReadChannel(responseBody),
            status = if (responseBody.contains("error")) HttpStatusCode.NotFound else HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
