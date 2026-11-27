package no.nav.emottak.ebms.async.leadership

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.async.log
import no.nav.emottak.message.model.Direction
import no.nav.emottak.utils.environment.getEnvVar
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

fun interface LeaderPodAddress {
    suspend fun resolve(podName: String): String
}

class KubernetesLeaderPodAddress(
    private val namespace: String,
    private val application: String,
    private val client: HttpClient,
    private val token: () -> String,
    private val apiUrl: String = "https://kubernetes.default.svc"
) : LeaderPodAddress {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun resolve(podName: String): String {
        require(POD_NAME.matches(podName)) { "Invalid elected pod name" }
        val pod = json.decodeFromString<Pod>(
            client.get("$apiUrl/api/v1/namespaces/$namespace/pods/$podName") {
                bearerAuth(withContext(Dispatchers.IO) { token() })
            }.bodyAsText()
        )
        require(
            pod.metadata.name == podName &&
                pod.metadata.namespace == namespace &&
                pod.metadata.labels["app"] == application &&
                pod.spec.serviceAccountName == application &&
                pod.metadata.deletionTimestamp == null &&
                pod.status.conditions.any { it.type == "Ready" && it.status == "True" }
        ) { "Elected pod is not a ready $application pod in $namespace" }
        val address = pod.status.podIP
        require(NUMERIC_IP.matches(address)) { "Elected pod has no valid IP address" }
        val ip = InetAddress.getByName(address)
        require(!ip.isAnyLocalAddress && !ip.isLoopbackAddress && !ip.isLinkLocalAddress && !ip.isMulticastAddress) {
            "Elected pod has an invalid forwarding address"
        }
        return URLBuilder().apply {
            protocol = URLProtocol.HTTP
            host = address
            port = 8080
        }.buildString().trimEnd('/')
    }

    @Serializable
    private data class Pod(val metadata: Metadata, val spec: Spec, val status: Status)

    @Serializable
    private data class Metadata(
        val name: String,
        val namespace: String,
        val labels: Map<String, String> = emptyMap(),
        val deletionTimestamp: String? = null
    )

    @Serializable
    private data class Spec(val serviceAccountName: String)

    @Serializable
    private data class Status(val podIP: String = "", val conditions: List<Condition> = emptyList())

    @Serializable
    private data class Condition(val type: String, val status: String)

    private companion object {
        val NUMERIC_IP = Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}|[0-9a-fA-F]*:[0-9a-fA-F:]+")
    }
}

class LeaderForwarder(
    private val leadership: Leadership,
    private val address: LeaderPodAddress,
    private val client: HttpClient,
    private val closeResources: () -> Unit = {}
) : AutoCloseable {
    suspend fun forwardIfFollower(call: ApplicationCall, direction: Direction, limit: Int): Boolean {
        if (leadership.isLeader) return false
        call.response.header(HttpHeaders.CacheControl, "no-store")
        val leader = leadership.leader.value
        if (leader == null || call.request.headers[FORWARDED_HEADER] != null) {
            call.respondText("No confirmed leader available for this retry batch.", status = HttpStatusCode.ServiceUnavailable)
            return true
        }
        var dispatched = false
        try {
            val base = address.resolve(leader)
            val path = if (direction == Direction.IN) "incoming" else "outgoing"
            dispatched = true
            val response = client.get("$base/api/retry/$path/limit/$limit") {
                header(FORWARDED_HEADER, "1")
                header(HttpHeaders.CacheControl, "no-store")
                call.request.headers[HttpHeaders.Authorization]?.let { header(HttpHeaders.Authorization, it) }
            }
            call.respondText(response.bodyAsText(), status = response.status)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Cannot forward retry batch to elected pod {}", leader, e)
            val message = if (dispatched) {
                "Leader request failed; the batch may have started. Check job progress before repeating."
            } else {
                "Leader discovery failed; the retry batch was not forwarded."
            }
            call.respondText(message, status = HttpStatusCode.ServiceUnavailable)
        }
        return true
    }

    override fun close() = closeResources()

    companion object {
        const val FORWARDED_HEADER = "X-Ebms-Retry-Forwarded"
    }
}

fun createLeaderForwarder(leadership: Leadership): LeaderForwarder? {
    if (leadership is LocalLeadership) return null
    val directory = Path.of("/var/run/secrets/kubernetes.io/serviceaccount")
    require(Files.isReadable(directory.resolve("token"))) { "Kubernetes API service-account token is not mounted" }
    val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null)
        Files.newInputStream(directory.resolve("ca.crt")).use {
            CertificateFactory.getInstance("X.509").generateCertificates(it).forEachIndexed { index, certificate ->
                setCertificateEntry("kubernetes-$index", certificate)
            }
        }
    }
    val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
        init(trustStore)
    }.trustManagers.filterIsInstance<X509TrustManager>().single()
    val kubernetesClient = HttpClient(CIO) {
        expectSuccess = true
        followRedirects = false
        install(HttpTimeout) { requestTimeoutMillis = 5_000 }
        engine { https { this.trustManager = trustManager } }
    }
    val forwardingClient = HttpClient(CIO) {
        followRedirects = false
        install(HttpTimeout) { requestTimeoutMillis = 10 * 60_000L }
    }
    return LeaderForwarder(
        leadership,
        KubernetesLeaderPodAddress(
            getEnvVar("NAIS_NAMESPACE"),
            getEnvVar("NAIS_APP_NAME"),
            kubernetesClient,
            { Files.readString(directory.resolve("token")).trim() }
        ),
        forwardingClient
    ) {
        forwardingClient.close()
        kubernetesClient.close()
    }
}
