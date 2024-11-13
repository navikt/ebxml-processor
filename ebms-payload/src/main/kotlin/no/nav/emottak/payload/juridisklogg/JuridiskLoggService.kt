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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.payload.log
import no.nav.emottak.util.getEnvVar
import no.nav.emottak.util.marker

class JuridiskLoggService() {
    private val juridiskLoggUrl = getEnvVar("APP_JURIDISKLOGG_URI", "https://app-q1.adeo.no/juridisklogg") + "/api/rest/logg"
    private val juridiskLoggStorageTime = getEnvVar("JURIDISKLOGG_STORAGE_TIME_YEARS", "1").toInt()
    private val userName = getEnvVar("JURIDESKLOGG_USERNAME", "dummyUsername")
    private val userPassword = getEnvVar("JURIDESKLOGG_PASSWORD", "dummyPassword")

    init {
        log.debug("Juridisk logg URL: $juridiskLoggUrl")
        log.debug("Juridisk logg user: $userName")
        log.debug("Juridisk logg password length: ${userPassword.length}")
    }

    fun logge(payloadRequest: PayloadRequest) {
        val httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
        }
        val request = JuridiskLoggRequest(
            payloadRequest.messageId,
            if (payloadRequest.direction == Direction.IN) "Ekstern bruker" else "NAV",
            if (payloadRequest.direction == Direction.IN) "NAV" else "Ekstern bruker",
            juridiskLoggStorageTime,
            java.util.Base64.getEncoder().encodeToString(payloadRequest.payload.bytes)
        )
        log.debug("Juridisk logg forespørsel: $request")

        val response = runBlocking {
            withContext(Dispatchers.IO) {
                try {
                    httpClient.post(juridiskLoggUrl) {
                        setBody(request)
                        contentType(ContentType.Application.Json)
                        basicAuth(userName, userPassword)
                    }.also {
                        log.debug(payloadRequest.marker(), "Juridisk logg respons: $it")
                    }.body<JuridiskLoggResponse>().also {
                        log.debug(payloadRequest.marker(), "Juridisk logg respons ID ${it.id}")
                    }
                } catch (e: Exception) {
                    log.error("Feil med å sende forespørsel til juridisk logg: ${e.message}", e)
                } finally {
                    httpClient.close()
                }
            }
        }
    }
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
