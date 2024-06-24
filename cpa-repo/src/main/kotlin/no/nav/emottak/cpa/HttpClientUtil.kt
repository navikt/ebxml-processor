package no.nav.emottak.cpa

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import no.nav.emottak.util.getEnvVar
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

private val httpProxyUrl = getEnvVar("HTTP_PROXY", "")

class HttpClientUtil {
    companion object {
        val client = HttpClient(CIO) {
            expectSuccess = true
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 1)
                exponentialDelay()
            }
            engine {
                if (httpProxyUrl.isNotBlank()) {
                    proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(URL(httpProxyUrl).host, URL(httpProxyUrl).port))
                }
            }
        }
    }
}
