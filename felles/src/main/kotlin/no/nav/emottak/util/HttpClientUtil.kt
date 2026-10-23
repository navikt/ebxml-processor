package no.nav.emottak.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.utils.common.model.EbmsProcessing
import no.nav.emottak.utils.common.model.SendInRequest
import no.nav.emottak.utils.environment.getEnvVar
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

private val httpProxyUrl = getEnvVar("HTTP_PROXY", "")

class HttpClientUtil {
    companion object {
        val client = HttpClient(CIO) {
            expectSuccess = true
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 1)
                exponentialDelay()
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 20_000
                requestTimeoutMillis = 30_000
            }
            engine {
                if (httpProxyUrl.isNotBlank()) {
                    proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(URI(httpProxyUrl).toURL().host, URI(httpProxyUrl).toURL().port))
                }
            }
        }
    }
}

fun PayloadMessage.toSendInRequest(partnerId: Long? = null): SendInRequest = SendInRequest(
    messageId = this.messageId,
    conversationId = this.conversationId,
    payloadId = this.payload.contentId,
    payload = this.payload.bytes,
    addressing = this.addressing,
    cpaId = this.cpaId,
    ebmsProcessing = EbmsProcessing(),
    signedOf = this.payload.signedBy,
    signedByPid = this.payload.signedByPid,
    signedByOrg = this.payload.signedByOrg,
    requestId = this.requestId,
    partnerId = partnerId
)
