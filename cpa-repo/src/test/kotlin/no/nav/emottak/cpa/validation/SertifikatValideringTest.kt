
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
        val sertifikatValidering = SertifikatValidering(crlChecker, trustStoreConfig)

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
        val crlRetriever = mockk<CRLRetriever>()
        every {
            runBlocking {
                crlRetriever.updateAllCRLs()
            }
        } returns listOf(crl)
        val crlChecker = CRLChecker(crlRetriever)
        val sertifikatValidering = SertifikatValidering(crlChecker, trustStoreConfig)

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
