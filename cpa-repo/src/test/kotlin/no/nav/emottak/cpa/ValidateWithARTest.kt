package no.nav.emottak.cpa

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.emottak.cpa.persistence.CPARepository
import no.nav.emottak.cpa.validation.AdresseregisterValidator
import no.nav.emottak.message.ebxml.EbXMLConstants.EBMS_SERVICE_URI
import no.nav.emottak.message.exception.CertificateValidationException
import no.nav.emottak.util.jsonLenient
import no.nav.emottak.validering.sertifikat.SertifikatValidator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidateWithARTest {

    @Test
    fun `Ugyldig signeringssertifikat fra adresseregisteret stopper validering`() {
        val engine = getFakeNhnAdresseregisterEngine()
        val adresseregisterValidator = AdresseregisterValidator(
            HttpClient(engine) {
                install(ContentNegotiation) {
                    jsonLenient()
                }
            }
        )
        val cpaRepository = mockk<CPARepository>()
        val sertifikatValidator = mockk<SertifikatValidator>()
        coEvery { sertifikatValidator.validateCertificate(any()) } throws CertificateValidationException("Sertifikat revokert")
        val validationRequest = createValidValidationRequest().let {
            it.copy(addressing = it.addressing.copy(service = EBMS_SERVICE_URI))
        }

        val exception = assertThrows<CertificateValidationException> {
            runBlocking {
                adresseregisterValidator.validateWithAR(cpaRepository, validationRequest, sertifikatValidator)
            }
        }

        assertEquals("Sertifikat revokert", exception.message)
        coVerify(exactly = 1) { sertifikatValidator.validateCertificate(any()) }
        val requestedPaths = engine.requestHistory.map { it.url.encodedPath }
        assertTrue(requestedPaths.any { it.endsWith("/8141253/signing") }, "$requestedPaths")
        assertTrue(requestedPaths.none { it.contains("communicationparty") || it.endsWith("/encryption") }, "$requestedPaths")
    }
}
