package no.nav.emottak.util

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod

class HttpClientUtil {

    private val client = HttpClient(CIO) {
        expectSuccess = true
    }

    suspend fun makeHttpRequest(urlString: String): ByteArray {
        val response: HttpResponse = client.request(urlString) {
            method = HttpMethod.Get
        }
        return response.body<ByteArray>()
    }
}