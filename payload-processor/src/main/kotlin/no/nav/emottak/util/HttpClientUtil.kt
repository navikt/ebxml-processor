package no.nav.emottak.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking


private val httpClientUtil = HttpClientUtil()
private val cpaRepoUrl = "http://cpa-repo"

fun hentKrypteringssertifikat(cpaId: String, herId: String): ByteArray = runBlocking {
    httpClientUtil.makeHttpRequest("$cpaRepoUrl/cpa/$cpaId/$herId/certificate/encryption").readBytes()
}

class HttpClientUtil {

    private val client = HttpClient(CIO) {
        expectSuccess = true
    }

    suspend fun makeHttpRequest(urlString: String): HttpResponse {
        val response: HttpResponse = client.request(urlString) {
            method = HttpMethod.Get
        }
        return response
    }
}