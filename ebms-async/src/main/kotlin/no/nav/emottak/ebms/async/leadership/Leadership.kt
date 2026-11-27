package no.nav.emottak.ebms.async.leadership

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.async.log
import no.nav.emottak.utils.environment.getEnvVar

interface Leadership : AutoCloseable {
    val podName: String
    val leader: StateFlow<String?>
    val isLeader: Boolean get() = leader.value == podName
    suspend fun watch()
    override fun close() {}
}

class LocalLeadership : Leadership {
    override val podName = "local"
    override val leader = MutableStateFlow<String?>(podName)
    override suspend fun watch(): Nothing = awaitCancellation()
}

class ElectorLeadership(
    override val podName: String,
    private val electorUrl: String,
    private val client: HttpClient = HttpClient(CIO) {
        expectSuccess = true
        followRedirects = false
        install(HttpTimeout) { requestTimeoutMillis = 2_000 }
    }
) : Leadership {
    override val leader = MutableStateFlow<String?>(null)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun refresh() {
        try {
            val name = json.decodeFromString<ElectedPod>(client.get(electorUrl).bodyAsText()).name
            require(POD_NAME.matches(name)) { "Elector returned an invalid pod name" }
            if (leader.value != name) log.info("Background-job leader changed to {}", name)
            leader.value = name
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            leader.value = null
            log.error("Cannot establish background-job leadership; leader-only work is disabled", e)
        }
    }

    override suspend fun watch() {
        try {
            while (currentCoroutineContext().isActive) {
                refresh()
                delay(2_000)
            }
        } finally {
            leader.value = null
        }
    }

    override fun close() = client.close()

    @Serializable
    private data class ElectedPod(val name: String)
}

internal val POD_NAME = Regex("[a-z0-9](?:[-a-z0-9.]*[a-z0-9])?")

fun createLeadership(registry: MeterRegistry): Leadership {
    val leadership = if (getEnvVar("NAIS_CLUSTER_NAME", "local") == "local") {
        LocalLeadership()
    } else {
        ElectorLeadership(
            podName = getEnvVar("NAIS_POD_NAME"),
            electorUrl = getEnvVar("ELECTOR_GET_URL")
        )
    }
    registry.gauge("background_job_leader", leadership) { if (it.isLeader) 1.0 else 0.0 }
    return leadership
}

class BackgroundJobUnavailable(message: String) : IllegalStateException(message)
