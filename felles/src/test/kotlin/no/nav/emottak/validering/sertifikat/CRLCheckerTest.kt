package no.nav.emottak.validering.sertifikat

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.emottak.crypto.KeyStoreManager
import org.bouncycastle.asn1.x500.X500Name
import java.math.BigInteger
import java.time.Instant
import java.util.Date

class CRLCheckerTest : FunSpec({

    val issuer = X500Name("CN=Test CA, O=Test Org, C=NO")
    val caKeyPair = CRLTestFactory.generateKeyPair()
    val caCertificate = CRLTestFactory.generateSelfSignedCaCertificate(issuer, caKeyPair)
    val trustStore = KeyStoreManager(InMemoryKeyStoreConfig(mapOf("test-ca" to caCertificate)))

    fun buildRetriever(crl: CRL, updateCRLFails: Boolean = false): CRLRetriever {
        val retriever = mockk<CRLRetriever>()
        coEvery { retriever.updateAllCRLs() } returns listOf(crl)
        if (updateCRLFails) {
            coEvery { retriever.updateCRL(any()) } throws RuntimeException("Nettverksfeil")
        }
        return retriever
    }

    test("Gyldig signert CRL uten revokerte sertifikater feiler ikke") {
        val now = Date()
        val crlFile = CRLTestFactory.generateCrl(
            issuer = issuer,
            signingKeyPair = caKeyPair,
            thisUpdate = Date(now.time - 60_000),
            nextUpdate = Date(now.time + 3_600_000)
        )
        val crl = CRL(issuer, "url", crlFile, Instant.now())
        val checker = CRLChecker(buildRetriever(crl), trustStore)

        shouldNotThrowAny {
            checker.getCRLRevocationInfo(issuer.toString(), BigInteger.valueOf(123))
        }
    }

    test("Gyldig signert CRL med revokert sertifikat feiler med Sertifikat revokert") {
        val now = Date()
        val revokedSerial = BigInteger.valueOf(42)
        val crlFile = CRLTestFactory.generateCrl(
            issuer = issuer,
            signingKeyPair = caKeyPair,
            thisUpdate = Date(now.time - 60_000),
            nextUpdate = Date(now.time + 3_600_000),
            revoked = listOf(revokedSerial to Date())
        )
        val crl = CRL(issuer, "url", crlFile, Instant.now())
        val checker = CRLChecker(buildRetriever(crl), trustStore)

        val exception = shouldThrow<CertificateValidationException> {
            checker.getCRLRevocationInfo(issuer.toString(), revokedSerial)
        }
        exception.message shouldContain "Sertifikat revokert"
    }

    test("CRL signert med feil CA feiler med signaturfeil") {
        val now = Date()
        val otherKeyPair = CRLTestFactory.generateKeyPair()
        val crlFile = CRLTestFactory.generateCrl(
            issuer = issuer,
            signingKeyPair = otherKeyPair, // signert med feil nøkkel
            thisUpdate = Date(now.time - 60_000),
            nextUpdate = Date(now.time + 3_600_000)
        )
        val crl = CRL(issuer, "url", crlFile, Instant.now())
        val checker = CRLChecker(buildRetriever(crl), trustStore)

        val exception = shouldThrow<CertificateValidationException> {
            checker.getCRLRevocationInfo(issuer.toString(), BigInteger.valueOf(1))
        }
        exception.message shouldContain "kunne ikke verifiseres"
    }

    test("Utløpt CRL feiler selv om periodisk oppdatering feiler") {
        val now = Date()
        val crlFile = CRLTestFactory.generateCrl(
            issuer = issuer,
            signingKeyPair = caKeyPair,
            thisUpdate = Date(now.time - 7_200_000),
            nextUpdate = Date(now.time - 3_600_000) // utløpt
        )
        val crl = CRL(issuer, "url", crlFile, Instant.now())
        val checker = CRLChecker(buildRetriever(crl, updateCRLFails = true), trustStore)

        val exception = shouldThrow<CertificateValidationException> {
            checker.getCRLRevocationInfo(issuer.toString(), BigInteger.valueOf(1))
        }
        exception.message shouldContain "utløpt"
    }

    test("CRL med thisUpdate frem i tid feiler") {
        val now = Date()
        val crlFile = CRLTestFactory.generateCrl(
            issuer = issuer,
            signingKeyPair = caKeyPair,
            thisUpdate = Date(now.time + 3_600_000), // ikke gyldig enda
            nextUpdate = Date(now.time + 7_200_000)
        )
        val crl = CRL(issuer, "url", crlFile, Instant.now())
        val checker = CRLChecker(buildRetriever(crl), trustStore)

        val exception = shouldThrow<CertificateValidationException> {
            checker.getCRLRevocationInfo(issuer.toString(), BigInteger.valueOf(1))
        }
        exception.message shouldContain "ikke gyldig enda"
    }

    test("Fortsatt gyldig CRL brukes videre når periodisk oppdatering feiler") {
        val now = Date()
        val crlFile = CRLTestFactory.generateCrl(
            issuer = issuer,
            signingKeyPair = caKeyPair,
            thisUpdate = Date(now.time - 60_000),
            nextUpdate = Date(now.time + 3_600_000) // fortsatt gyldig
        )
        // "updated" er gammel nok til å trigge et oppdateringsforsøk (> crlMaximumAgeInSeconds),
        // men selve CRL-en er fortsatt innenfor sitt gyldighetsvindu.
        val crl = CRL(issuer, "url", crlFile, Instant.now().minusSeconds(7_200))
        val checker = CRLChecker(buildRetriever(crl, updateCRLFails = true), trustStore)

        shouldNotThrowAny {
            checker.getCRLRevocationInfo(issuer.toString(), BigInteger.valueOf(1))
        }
    }
})
