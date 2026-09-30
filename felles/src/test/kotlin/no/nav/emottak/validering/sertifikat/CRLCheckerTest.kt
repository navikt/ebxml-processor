package no.nav.emottak.validering.sertifikat

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.emottak.message.exception.CertificateValidationException
import no.nav.emottak.util.TestUtil
import org.bouncycastle.asn1.x500.X500Name
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.security.cert.X509CRL
import java.time.Instant
import java.util.Date
import javax.security.auth.x500.X500Principal

class CRLCheckerTest {
    @Test
    fun `unsupported issuer fails without retrieving CRLs`() {
        val exception = shouldThrow<CRLException> {
            runBlocking {
                CRLChecker(CRLStore()).getCRLRevocationInfo("CN=Unsupported", BigInteger.ONE)
            }
        }

        exception.message shouldContain "ikke støttet"
    }

    @Test
    fun `missing CRL fails closed`() {
        val issuer = X500Name("CN=Supported")
        val crl = CRL(issuer, "https://example.test/crl", null)

        val exception = shouldThrow<CRLException> {
            runBlocking {
                CRLChecker(CRLStore(listOf(crl))).getCRLRevocationInfo(issuer.toString(), BigInteger.ONE)
            }
        }

        exception.message shouldContain "henting av CRL har feilet"
    }

    @Test
    fun `expired CRL fails closed`() {
        val issuer = X500Name("CN=Supported")
        val crlFile = mockk<X509CRL>()
        every { crlFile.issuerX500Principal } returns X500Principal(issuer.toString())
        every { crlFile.nextUpdate } returns Date.from(Instant.now().minusSeconds(1))
        val crl = CRL(issuer, "https://example.test/crl", crlFile)

        val exception = shouldThrow<CRLException> {
            runBlocking {
                CRLChecker(CRLStore(listOf(crl))).getCRLRevocationInfo(issuer.toString(), BigInteger.ONE)
            }
        }

        exception.message shouldContain "er utløpt"
    }

    @Test
    fun `CRL from another issuer fails closed`() {
        val issuer = X500Name("CN=Expected")
        val crlFile = mockk<X509CRL>()
        every { crlFile.issuerX500Principal } returns X500Principal("CN=Actual")
        val crl = CRL(issuer, "https://example.test/crl", crlFile)

        val exception = shouldThrow<CRLException> {
            runBlocking {
                CRLChecker(CRLStore(listOf(crl))).getCRLRevocationInfo(issuer.toString(), BigInteger.ONE)
            }
        }

        exception.message shouldContain "men forventet"
    }

    @Test
    fun `revoked certificate is rejected from stored CRL`() {
        val crlFile = mockk<X509CRL>()
        every { crlFile.issuerX500Principal } returns TestUtil.crlFile.issuerX500Principal
        every { crlFile.nextUpdate } returns Date.from(Instant.now().plusSeconds(3600))
        every { crlFile.getRevokedCertificate(any<BigInteger>()) } answers {
            TestUtil.crlFile.getRevokedCertificate(firstArg<BigInteger>())
        }
        val crl = CRL(
            X500Name(crlFile.issuerX500Principal.name),
            "https://example.test/crl",
            crlFile
        )

        val exception = shouldThrow<CertificateValidationException> {
            runBlocking {
                CRLChecker(CRLStore(listOf(crl))).getCRLRevocationInfo(
                    TestUtil.revokedCertificate.issuerX500Principal.name,
                    TestUtil.revokedCertificate.serialNumber
                )
            }
        }

        exception.message shouldContain "Sertifikat revokert"
    }
}
