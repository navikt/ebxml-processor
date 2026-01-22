package no.nav.emottak.payload.helseid

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import kotlinx.coroutines.runBlocking
import no.nav.emottak.payload.helseid.testutils.HelseIDCreator
import no.nav.emottak.payload.helseid.testutils.ResourceUtil
import no.nav.emottak.payload.helseid.testutils.XMLUtil
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64
import kotlin.test.assertFailsWith

class NinResolverTest {

    val VALID_AUDIENCE = "nav:sign-message"
    val VALID_SCOPE = "nav:sign-message/msghead"
    val helseIDCreator = HelseIDCreator("helseid/keystore.jks", "jks", "123456789".toCharArray())

    private fun buildToken(pid: String, audiences: List<String>, scopes: List<String>) = helseIDCreator.getToken(
        alias = "docsigner",
        pid = pid,
        scopes = scopes,
        audiences = audiences,
        algo = JWSAlgorithm.RS256,
        type = JOSEObjectType.JWT
    )

    @Test
    fun `validate with OK helseID token`() {
        val myId = "01010000110"
        val validToken = buildToken(myId, listOf(VALID_AUDIENCE),listOf(VALID_SCOPE))

        val resolver = NinResolver()
        val id = resolver.resolve(Base64.getEncoder().encodeToString(validToken.toByteArray()), Instant.now())
        assert(id == myId)
    }

    @Test
    fun `validate with failing helseID token`() {
        val token = buildToken("whateverId", listOf("whatever_audience"),listOf(VALID_SCOPE))

        val resolver = NinResolver()
        assertFailsWith(IllegalStateException::class, "Token does not contain required audience") {
            resolver.resolve(Base64.getEncoder().encodeToString(token.toByteArray()), Instant.now())
        }
    }

    @Test
    fun `validate with document missing helseID`() {
        val doc = XMLUtil.createDocument(
            Base64.getDecoder().decode(ResourceUtil.getStringClasspathResource("helseid/testdata/m1.with.attachment.not.helseid.b64"))
        )

        val resolver = NinResolver()
        assertFailsWith(RuntimeException::class, "No HelseID token found in document") {
            runBlocking {
                resolver.resolve(doc)
            }
        }
    }
}
