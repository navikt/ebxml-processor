package no.nav.emottak.payload.helseid

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.emottak.payload.helseid.testutils.HelseIDCreator
import no.nav.emottak.payload.helseid.testutils.ResourceUtil
import no.nav.emottak.payload.helseid.testutils.XMLUtil
import no.nav.emottak.payload.ocspstatus.OcspStatusService
import no.nav.emottak.payload.ocspstatus.SertifikatInfo
import org.junit.jupiter.api.Test
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64
import kotlin.test.assertEquals
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
        val validToken = buildToken(myId, listOf(VALID_AUDIENCE), listOf(VALID_SCOPE))

        val resolver = NinResolver()
        val id = resolver.resolve(Base64.getEncoder().encodeToString(validToken.toByteArray()), Instant.now())
        assert(id == myId)
    }

    @Test
    fun `validate with failing helseID token`() {
        val token = buildToken("whateverId", listOf("whatever_audience"), listOf(VALID_SCOPE))

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
        val mockCertificate = mockk<X509Certificate>()
        val mockInfo = mockk<SertifikatInfo>()
        val mockOcspStatusService = mockk<OcspStatusService>()
        coEvery {
            mockInfo.fnr
        }.returns("theFnr")
        coEvery {
            mockOcspStatusService.getOCSPStatus(mockCertificate)
        }.returns(mockInfo)

        val resolver = NinResolver(ocspStatusService = mockOcspStatusService)
        runBlocking {
            val resolved = resolver.resolve(doc, mockCertificate)
            assertEquals("theFnr", resolved)
        }
    }
}
