package no.nav.emottak.payload.helseid

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.RemoteKeySourceException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.emottak.payload.error.HelseIdTokenException
import no.nav.emottak.payload.helseid.testutils.HelseIDCreator
import no.nav.emottak.payload.helseid.testutils.ResourceUtil
import no.nav.emottak.payload.helseid.testutils.XMLUtil
import no.nav.emottak.payload.ocspstatus.OcspStatusService
import no.nav.emottak.payload.ocspstatus.SertifikatInfo
import org.junit.jupiter.api.Test
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64
import java.util.TimeZone
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
        val thrown = assertFailsWith<HelseIdTokenException> {
            resolver.resolve(Base64.getEncoder().encodeToString(token.toByteArray()), Instant.now())
        }
        assertEquals("Invalid HelseID token: Token does not contain required audience", thrown.message)
    }

    @Test
    fun `invalid helseID token in document is reported as HelseIdTokenException`() {
        val doc = helseIdDocument()
        val tokenValidator = mockk<HelseIdTokenValidator>()
        every { tokenValidator.getHelseIdTokenFromDocument(doc) } returns "token"
        every { tokenValidator.getValidatedNin("token", any()) } throws IllegalStateException("Invalid issuer foo")

        val resolver = NinResolver(tokenValidator = tokenValidator, ocspStatusService = mockk())
        val thrown = assertFailsWith<HelseIdTokenException> {
            runBlocking { resolver.resolve(doc, mockk<X509Certificate>()) }
        }
        assertEquals("Invalid HelseID token: Invalid issuer foo", thrown.message)
    }

    @Test
    fun `unavailable HelseID JWKS propagates as transient error`() {
        val doc = helseIdDocument()
        val tokenValidator = mockk<HelseIdTokenValidator>()
        every { tokenValidator.getHelseIdTokenFromDocument(doc) } returns "token"
        every { tokenValidator.getValidatedNin("token", any()) } throws RemoteKeySourceException("JWKS unreachable", null)

        val resolver = NinResolver(tokenValidator = tokenValidator, ocspStatusService = mockk())
        assertFailsWith<RemoteKeySourceException> {
            runBlocking { resolver.resolve(doc, mockk<X509Certificate>()) }
        }
    }

    private fun helseIdDocument() = XMLUtil.createDocument(
        Base64.getDecoder().decode(ResourceUtil.getStringClasspathResource("helseid/testdata/m1.helseid.ok.b64"))
    )

    @Test
    fun `parseDateOrThrow interprets zoneless GenDate as Europe-Oslo regardless of system default timezone`() {
        val defaultTimeZone = TimeZone.getDefault()
        val zonelessDate = "2024-06-15T10:00:00"
        val expectedInstant = LocalDateTime.parse(zonelessDate).atZone(ZoneId.of("Europe/Oslo")).toInstant()

        val method = NinResolver::class.java.getDeclaredMethod("parseDateOrThrow", String::class.java)
        method.isAccessible = true

        try {
            // Set the JVM default timezone to something other than Europe/Oslo to verify
            // that the fallback parsing of a zoneless GenDate is not affected by it.
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

            val resolver = NinResolver()
            val actualInstant = method.invoke(resolver, zonelessDate) as Instant

            assertEquals(expectedInstant, actualInstant)
        } finally {
            TimeZone.setDefault(defaultTimeZone)
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
