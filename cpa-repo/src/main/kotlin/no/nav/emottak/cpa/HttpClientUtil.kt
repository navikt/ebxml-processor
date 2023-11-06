package no.nav.emottak.cpa

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

class HttpClientUtil {
    companion object {
        val client = HttpClient(CIO) {
            expectSuccess = true
        }
    }
}