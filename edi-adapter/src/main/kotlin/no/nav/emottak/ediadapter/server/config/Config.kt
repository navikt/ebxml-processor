package no.nav.emottak.ediadapter.server.config

import io.ktor.client.plugins.logging.LogLevel
import java.net.URI
import kotlin.time.Duration

data class Config(
    val nhn: Nhn,
    val nhnOAuth: NhnOAuth,
    val server: Server,
    val httpClient: HttpClient,
    val httpTokenClient: HttpTokenClient,
    val azureAuth: AzureAuth
)

data class Server(
    val port: Port,
    val preWait: Duration
)

@JvmInline
value class Port(val value: Int)

data class Nhn(
    val baseUrl: URI,
    val keyPairPath: KeyPairPath
) {
    @JvmInline
    value class KeyPairPath(val value: String)
}

data class NhnOAuth(
    val keyId: KeyId,
    val clientId: ClientId,
    val audience: Audience,
    val tokenEndpoint: URI,
    val scope: Scope,
    val grantType: GrantType,
    val clientAssertionType: ClientAssertionType
) {
    @JvmInline
    value class KeyId(val value: String)

    @JvmInline
    value class ClientId(val value: String)

    @JvmInline
    value class Audience(val value: String)

    @JvmInline
    value class Scope(val value: String)

    @JvmInline
    value class GrantType(val value: String)

    @JvmInline
    value class ClientAssertionType(val value: String)
}

data class AzureAuth(
    val issuer: Issuer,
    val appName: AppName,
    val appScope: AppScope,
    val clusterName: ClusterName,
    val azureWellKnownUrl: AzureWellKnownUrl,
    val acceptedAudience: AcceptedAudience
) {
    @JvmInline
    value class Issuer(val value: String)

    @JvmInline
    value class AppName(val value: String)

    @JvmInline
    value class ClusterName(val value: String)

    @JvmInline
    value class AppScope(val value: String)

    @JvmInline
    value class AzureWellKnownUrl(val value: String)

    @JvmInline
    value class AcceptedAudience(val value: String)
}

@JvmInline
value class Timeout(val value: Long)

data class HttpClient(
    val connectionTimeout: Timeout,
    val apiVersionHeader: ApiVersionHeader,
    val sourceSystemHeader: SourceSystemHeader,
    val acceptTypeHeader: AcceptTypeHeader,
    val logLevel: LogLevel
) {
    @JvmInline
    value class ApiVersionHeader(val value: String)

    @JvmInline
    value class SourceSystemHeader(val value: String)

    @JvmInline
    value class AcceptTypeHeader(val value: String)
}

data class HttpTokenClient(val connectionTimeout: Timeout)
