package no.nav.emottak.payload.juridisklogg

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.basicAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.payload.log
import no.nav.emottak.util.getEnvVar
import java.io.FileInputStream

class JuridiskLoggService() {
    private val juridiskLoggUrl = getEnvVar("APP_JURIDISKLOGG_URI") + "/api/rest/logg"
    private val juridiskLoggStorageTime = getEnvVar("JURIDISKLOGG_STORAGE_TIME_YEARS").toIntOrNull() ?: 1
    private val userName = lazy { String(FileInputStream("/var/run/secrets/nais.io/vault/serviceuser/username").readAllBytes()) }
    private val userPassword = lazy { String(FileInputStream("/var/run/secrets/nais.io/vault/serviceuser/password").readAllBytes()) }

    init {
        log.debug("Juridisk logg URL: $juridiskLoggUrl")
        log.debug("Juridisk logg user: ${userName.value}")
        log.debug("Juridisk logg password length: ${userPassword.value.length}")
    }

    fun logge(payloadRequest: PayloadRequest) {
        val httpClient = HttpClient(CIO)
        val request = JuridiskLoggRequest(
            payloadRequest.messageId,
            if (payloadRequest.direction == Direction.IN) "Ekstern bruker" else "NAV",
            if (payloadRequest.direction == Direction.IN) "NAV" else "Ekstern bruker",
            juridiskLoggStorageTime,
            payloadRequest.payload.bytes
        )
        log.debug("Juridisk logg forespørsel: $request")

        val response = suspend {
            withContext(Dispatchers.IO) {
                try {
                    httpClient.post(juridiskLoggUrl) {
                        setBody(request)
                        contentType(ContentType.Application.Json)
                        basicAuth(userName.value, userPassword.value)
                    }.body<JuridiskLoggResponse>()
                } catch (e: Exception) {
                    log.error("Feil med å sende forespørsel til juridisk logg", e)
                } finally {
                    httpClient.close()
                }
            }
        }
        log.debug("Juridisk logg respons: $response")
    }
}

@Serializable
data class JuridiskLoggRequest(
    val meldingsId: String,
    val avsender: String,
    val mottaker: String,
    val antallAarLagres: Int = 10,
    val meldingsInnhold: ByteArray
)

@Serializable
data class JuridiskLoggResponse(
    val id: String
)
