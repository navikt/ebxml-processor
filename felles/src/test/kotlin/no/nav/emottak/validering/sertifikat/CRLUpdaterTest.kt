package no.nav.emottak.validering.sertifikat

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import no.nav.emottak.crypto.KeyStoreManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v2CRLBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

class CRLUpdaterTest {
    private val issuer = X500Name("CN=Test CA, O=NAV, C=NO")
    private val url = "http://crl.test/ca.crl"
    private val issuerList = mapOf(issuer.toString() to url)
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    @Test
    fun `refresh downloads and publishes CRLs`() = runBlocking<Unit> {
        val store = CRLStore(issuerList)

        updater(serving { crlBytes() }, store).refresh()

        store.get(issuer).shouldNotBeNull().file.shouldNotBeNull()
    }

    @Test
    fun `failed download preserves last successfully downloaded CRL`() = runBlocking<Unit> {
        val store = CRLStore(issuerList)
        val responses = AtomicInteger()
        val client = serving { if (responses.getAndIncrement() == 0) crlBytes() else null }
        val updater = updater(client, store)

        updater.refresh()
        val existing = store.get(issuer).shouldNotBeNull()
        updater.refresh()

        store.get(issuer) shouldBe existing
    }

    @Test
    fun `successful refresh replaces previous CRL`() = runBlocking<Unit> {
        val store = CRLStore(issuerList)
        val updater = updater(serving { crlBytes() }, store)

        updater.refresh()
        val first = store.get(issuer).shouldNotBeNull()
        updater.refresh()

        (store.get(issuer).shouldNotBeNull().file === first.file) shouldBe false
    }

    @Test
    fun `expired or mismatched downloaded CRL is not published`() = runBlocking<Unit> {
        listOf(
            crlBytes(nextUpdate = Instant.now().minusSeconds(60)),
            crlBytes(crlIssuer = X500Name("CN=Other CA"))
        ).forEach { invalid ->
            val store = CRLStore(issuerList)

            updater(serving { invalid }, store).refresh()

            store.get(issuer).shouldNotBeNull().file.shouldBeNull()
        }
    }

    @Test
    fun `CRL with invalid signature or future thisUpdate is not published`() = runBlocking<Unit> {
        val otherKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private
        listOf(
            crlBytes(signingKey = otherKey),
            crlBytes(thisUpdate = Instant.now().plusSeconds(600))
        ).forEach { invalid ->
            val store = CRLStore(issuerList)

            updater(serving { invalid }, store).refresh()

            store.get(issuer).shouldNotBeNull().file.shouldBeNull()
        }
    }

    @Test
    fun `CRL is not published when issuer certificate is missing from truststore`() = runBlocking<Unit> {
        val store = CRLStore(issuerList)
        val otherCa = CRLTestFactory.generateSelfSignedCaCertificate(X500Name("CN=Other CA"), keyPair)

        CRLUpdater(
            serving { crlBytes() },
            store,
            Duration.ofHours(1),
            issuerList,
            KeyStoreManager(InMemoryKeyStoreConfig(mapOf("other-ca" to otherCa)))
        ).refresh()

        store.get(issuer).shouldNotBeNull().file.shouldBeNull()
    }

    @Test
    fun `invalid downloaded CRL preserves last valid CRL`() = runBlocking<Unit> {
        val store = CRLStore(issuerList)
        val otherKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private
        val responses = AtomicInteger()
        val client = serving {
            if (responses.getAndIncrement() == 0) crlBytes() else crlBytes(signingKey = otherKey)
        }
        val updater = updater(client, store)

        updater.refresh()
        val existing = store.get(issuer).shouldNotBeNull()
        updater.refresh()

        store.get(issuer) shouldBe existing
    }

    @Test
    fun `periodic updater refreshes until cancelled`() = runBlocking<Unit> {
        val downloads = AtomicInteger()
        val downloadedTwice = CompletableDeferred<Unit>()
        val client = serving {
            if (downloads.incrementAndGet() == 2) downloadedTwice.complete(Unit)
            crlBytes()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job = updater(client, CRLStore(issuerList), Duration.ofMillis(10)).startIn(scope)

        try {
            withTimeout(1_000) { downloadedTwice.await() }
            job.cancelAndJoin()
            val downloadsAfterCancellation = downloads.get()
            delay(30)

            downloads.get() shouldBe downloadsAfterCancellation
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `refresh interval must be positive`() {
        val result = runCatching { updater(serving { null }, CRLStore(), Duration.ZERO) }

        (result.exceptionOrNull() is IllegalArgumentException) shouldBe true
    }

    private fun updater(client: HttpClient, store: CRLStore, interval: Duration = Duration.ofHours(1)) =
        CRLUpdater(
            client,
            store,
            interval,
            issuerList,
            KeyStoreManager(
                InMemoryKeyStoreConfig(
                    mapOf("test-ca" to CRLTestFactory.generateSelfSignedCaCertificate(issuer, keyPair))
                )
            )
        )

    private fun serving(body: () -> ByteArray?) = HttpClient(
        MockEngine {
            body()?.let { respond(it) } ?: respondError(HttpStatusCode.InternalServerError)
        }
    )

    private fun crlBytes(
        crlIssuer: X500Name = issuer,
        thisUpdate: Instant = Instant.now().minusSeconds(60),
        nextUpdate: Instant = Instant.now().plusSeconds(3600),
        signingKey: PrivateKey = keyPair.private
    ): ByteArray {
        return X509v2CRLBuilder(crlIssuer, Date.from(thisUpdate))
            .setNextUpdate(Date.from(nextUpdate))
            .build(JcaContentSignerBuilder("SHA256withRSA").build(signingKey))
            .encoded
    }
}
