package no.nav.emottak.payload.juridisklogg

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.basicAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import no.nav.emottak.message.model.PartyId
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.payload.log
import no.nav.emottak.util.getEnvVar
import no.nav.emottak.util.marker

class JuridiskLoggService() {
    private val juridiskLoggUrl = getEnvVar("APP_JURIDISKLOGG_URI", "https://app-q1.adeo.no/juridisklogg") + "/api/rest/logg"
    private val juridiskLoggStorageTime = getEnvVar("JURIDISKLOGG_STORAGE_TIME_YEARS", "1").toInt()
    private val userName = getEnvVar("JURIDESKLOGG_USERNAME", "dummyUsername")
    private val userPassword = getEnvVar("JURIDESKLOGG_PASSWORD", "dummyPassword")

    suspend fun logge(payloadRequest: PayloadRequest): String? {
        var juridiskLoggRecordId: String? = null
        val httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
        }

        val avsender: String = choosePartyID(payloadRequest.addressing.from.partyId)
        val mottaker: String = choosePartyID(payloadRequest.addressing.to.partyId)

        val request = JuridiskLoggRequest(
            payloadRequest.messageId,
            avsender,
            mottaker,
            juridiskLoggStorageTime,
            java.util.Base64.getEncoder().encodeToString(payloadRequest.payload.bytes)
        )
        log.debug(payloadRequest.marker(), "Juridisk logg request: $request")

        withContext(Dispatchers.IO) {
            try {
                httpClient.post(juridiskLoggUrl) {
                    setBody(request)
                    contentType(ContentType.Application.Json)
                    basicAuth(userName, userPassword)
                }.also {
                    log.debug(payloadRequest.marker(), "Juridisk logg response: $it")
                }.body<JuridiskLoggResponse>().also {
                    juridiskLoggRecordId = it.id
                }
                log.info(payloadRequest.marker(), "Message saved to juridisk logg")
            } catch (e: Exception) {
                log.error(payloadRequest.marker(), "Exception occurred during sending message to juridisk logg: ${e.message}", e)
                throw e
            } finally {
                httpClient.close()
            }
        }
        return juridiskLoggRecordId
    }
}

private fun choosePartyID(partyIDs: List<PartyId>): String {
    val partyId = partyIDs.firstOrNull { it.type == "orgnummer" }
        ?: partyIDs.firstOrNull { it.type == "HER" }
        ?: partyIDs.firstOrNull { it.type == "ENH" }
        ?: partyIDs.first()

    return "${partyId.type}: ${partyId.value}"
}

@Serializable
data class JuridiskLoggRequest(
    val meldingsId: String,
    val avsender: String,
    val mottaker: String,
    val antallAarLagres: Int = 10,
    val meldingsInnhold: String
)

@Serializable
data class JuridiskLoggResponse(
    val id: String
)
