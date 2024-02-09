package no.nav.emottak.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import no.nav.emottak.melding.model.PartyId

private val httpClientUtil = HttpClientUtil()
private val cpaRepoUrl = "http://cpa-repo"

fun hentKrypteringssertifikat(cpaId: String, partyId: PartyId): ByteArray = runBlocking {
    httpClientUtil.makeHttpRequest("$cpaRepoUrl/cpa/$cpaId/party/${partyId.type}/${partyId.value }/encryption/certificate").readBytes()
}

class HttpClientUtil {
    private val client = HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun makeHttpRequest(urlString: String): HttpResponse {
        val response: HttpResponse = client.request(urlString) {
            method = HttpMethod.Get
        }
        return response
    }
}
