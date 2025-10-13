
package no.nav.emottak.cpa.validation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import no.nav.emottak.cpa.TestUtil
import no.nav.emottak.cpa.TestUtil.Companion.crlFile
import no.nav.emottak.cpa.cert.CRL
import no.nav.emottak.cpa.cert.CRLChecker
import no.nav.emottak.cpa.cert.CRLRetriever
import no.nav.emottak.cpa.cert.CertificateValidationException
import no.nav.emottak.util.createX509Certificate
import no.nav.emottak.util.decodeBase64
import org.bouncycastle.asn1.x500.X500Name
import java.time.Instant

class SertifikatValideringTest : FunSpec({

    context("Sertifikatsjekk med mocked CRLChecker") {
        val crlChecker = mockk<CRLChecker>()
        every {
            crlChecker.getCRLRevocationInfo(any(), any())
        } just runs
        System.setProperty("TRUSTSTORE_PATH", "truststore.p12")
        val sertifikatValidering = SertifikatValidering(crlChecker)

        withData(
            mapOf(
                "Selvsignert sertifikat feiler" to row(TestUtil.selfSignedCertificate, "Sertifikat er selvsignert"),
                "Expired sertifikat feiler" to row(TestUtil.expiredCertificate, "Sertifikat utløpt"),
                "Gyldig sertifikat med ukjent trust chain feiler" to row(TestUtil.validCertificate, "Sertifikatvalidering feilet")
            )
        ) { (certificate, errorMessage) ->
            val exception = shouldThrow<CertificateValidationException> {
                sertifikatValidering.validateCertificate(certificate)
            }
            exception.message shouldStartWith errorMessage
        }

        withData(
            mapOf(
                "Gyldig sertifikat er OK" to sertifikat
            )
        ) { certificate ->
            sertifikatValidering.validateCertificate(certificate)
        }
    }

    context("Sertifikatsjekk med CRLChecker med CRL fil") {
        val crl = CRL(
            X500Name("CN=Buypass Class 3 CA 2, O=Buypass AS-983163327, C=NO"),
            "url",
            crlFile,
            Instant.now()
        )
        System.setProperty("TRUSTSTORE_PATH", "truststore.p12")
        val crlRetriever = mockk<CRLRetriever>()
        every {
            runBlocking {
                crlRetriever.updateAllCRLs()
            }
        } returns listOf(crl)
        val crlChecker = CRLChecker(crlRetriever)
        val sertifikatValidering = SertifikatValidering(crlChecker)

        withData(
            mapOf(
                "Revokert sertifikat feiler" to row(TestUtil.revokedCertificate, "Sertifikat revokert")
            )
        ) { (certificate, errorMessage) ->
            val exception = shouldThrow<CertificateValidationException> {
                sertifikatValidering.sjekkCRL(certificate)
            }
            exception.message shouldStartWith errorMessage
        }
    }
})

val sertifikat = createX509Certificate(
    decodeBase64("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".toByteArray())
)
