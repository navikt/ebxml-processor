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


private val httpClientUtil = HttpClientUtil()
private val cpaRepoUrl = "http://cpa-repo"

fun hentKrypteringssertifikat(cpaId: String, partyType: String, partyId: String): ByteArray = runBlocking {
    httpClientUtil.makeHttpRequest("$cpaRepoUrl/cpa/$cpaId/party/$partyType/$partyId/encryption/certificate").readBytes()
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