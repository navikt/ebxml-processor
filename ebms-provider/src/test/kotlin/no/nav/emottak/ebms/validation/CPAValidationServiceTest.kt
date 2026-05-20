package no.nav.emottak.ebms.validation

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.CpaRepoClient
import no.nav.emottak.ebms.model.validateSignature
import no.nav.emottak.message.exception.EbmsException
import no.nav.emottak.message.model.Acknowledgment
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Feil
import no.nav.emottak.message.model.PartyCertificates
import no.nav.emottak.message.model.PayloadProcessing
import no.nav.emottak.message.model.ProcessConfig
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.message.model.ValidationResult
import no.nav.emottak.util.signatur.SignatureException
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.utils.common.model.EbmsProcessing
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import org.apache.xml.security.algorithms.MessageDigestAlgorithm
import org.apache.xml.security.signature.XMLSignature
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CPAValidationServiceTest {

    private val cpaRepoClient = mockk<CpaRepoClient>()
    private val service = CPAValidationService(cpaRepoClient)

    private val testCpaId = "nav:qass:35065"
    private val testAddressing = Addressing(
        to = Party(listOf(PartyId("HER", "79768")), "KontrollUtbetaler"),
        from = Party(listOf(PartyId("HER", "8141253")), "Behandler"),
        service = "BehandlerKrav",
        action = "OppgjorsMelding"
    )
    private val testSignatureDetails = SignatureDetails(
        certificate = byteArrayOf(1, 2, 3),
        signatureAlgorithm = XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
        hashFunction = MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256
    )
    private val testAcknowledgment = Acknowledgment(
        requestId = "test-request-id",
        messageId = "test-message-id",
        refToMessageId = "test-ref-message-id",
        conversationId = "test-conversation-id",
        cpaId = testCpaId,
        addressing = testAddressing
    )
    private val validValidationResult = ValidationResult(
        ebmsProcessing = EbmsProcessing(),
        payloadProcessing = PayloadProcessing(
            signingCertificate = testSignatureDetails,
            encryptionCertificate = byteArrayOf(1, 2, 3),
            processConfig = ProcessConfig(
                kryptering = false,
                komprimering = false,
                signering = false,
                internformat = false,
                validering = false,
                apprec = false,
                ocspSjekk = false,
                juridiskLogg = false,
                adapter = null,
                errorAction = null
            )
        )
    )

    @BeforeEach
    fun setUp() {
        mockkStatic("no.nav.emottak.ebms.model.EbmsMessageExtKt")
    }

    @Test
    fun `validateIncomingSignalSignature - succeeds when first cert validates`() = runBlocking {
        coEvery { cpaRepoClient.getPartyCertificates(testCpaId) } returns listOf(
            PartyCertificates(listOf(PartyId("HER", "8141253")), testSignatureDetails, null),
            PartyCertificates(listOf(PartyId("HER", "79768")), testSignatureDetails, null)
        )
        every { any<EbmsMessage>().validateSignature(any()) } returns Unit

        service.validateIncomingSignalSignature(testAcknowledgment)
    }

    @Test
    fun `validateIncomingSignalSignature - succeeds when first cert fails but second succeeds`() = runBlocking {
        coEvery { cpaRepoClient.getPartyCertificates(testCpaId) } returns listOf(
            PartyCertificates(listOf(PartyId("HER", "8141253")), testSignatureDetails, null),
            PartyCertificates(listOf(PartyId("HER", "79768")), testSignatureDetails, null)
        )
        every { any<EbmsMessage>().validateSignature(testSignatureDetails) } throws SignatureException("Signatur feilet") andThen Unit

        service.validateIncomingSignalSignature(testAcknowledgment)
    }

    @Test
    fun `validateIncomingSignalSignature - throws EbmsException when all certs fail`() {
        coEvery { cpaRepoClient.getPartyCertificates(testCpaId) } returns listOf(
            PartyCertificates(listOf(PartyId("HER", "8141253")), testSignatureDetails, null),
            PartyCertificates(listOf(PartyId("HER", "79768")), testSignatureDetails, null)
        )
        every { any<EbmsMessage>().validateSignature(any()) } throws SignatureException("Signatur feilet")

        val exception = assertThrows<EbmsException> {
            runBlocking { service.validateIncomingSignalSignature(testAcknowledgment) }
        }
        assertEquals(ErrorCode.SECURITY_FAILURE, exception.feil.first().code)
    }

    @Test
    fun `validateIncomingSignalSignature - does not throw when no certs have signatureDetails`() = runBlocking {
        coEvery { cpaRepoClient.getPartyCertificates(testCpaId) } returns listOf(
            PartyCertificates(listOf(PartyId("HER", "8141253")), signatureDetails = null, encryptionCertificate = null)
        )

        service.validateIncomingSignalSignature(testAcknowledgment)
    }

    @Test
    fun `validateIncomingSignalSignature - does not throw when party list is empty`() = runBlocking {
        coEvery { cpaRepoClient.getPartyCertificates(testCpaId) } returns emptyList()

        service.validateIncomingSignalSignature(testAcknowledgment)
    }

    @Test
    fun `validateResult - throws EbmsException when validation result is invalid`() {
        val invalidResult = ValidationResult(error = listOf(Feil(ErrorCode.DELIVERY_FAILURE, "CPA ikke funnet")))

        assertThrows<EbmsException> {
            service.validateResult(invalidResult, testAcknowledgment, checkSignature = false)
        }
    }

    @Test
    fun `validateResult - throws EbmsException with SECURITY_FAILURE when signature check fails`() {
        every { any<EbmsMessage>().validateSignature(any()) } throws SignatureException("Ugyldig signatur")

        val exception = assertThrows<EbmsException> {
            service.validateResult(validValidationResult, testAcknowledgment, checkSignature = true)
        }
        assertEquals(ErrorCode.SECURITY_FAILURE, exception.feil.first().code)
    }

    @Test
    fun `validateResult - returns result when valid and signature OK`() {
        every { any<EbmsMessage>().validateSignature(any()) } returns Unit

        val result = service.validateResult(validValidationResult, testAcknowledgment, checkSignature = true)

        assertNotNull(result)
    }

    @Test
    fun `validateResult - skips signature check when checkSignature is false`() {
        val result = service.validateResult(validValidationResult, testAcknowledgment, checkSignature = false)

        assertNotNull(result)
    }

    @Test
    fun `validateIncomingMessage - throws EbmsException when CPA validation fails`() {
        val invalidResult = ValidationResult(error = listOf(Feil(ErrorCode.DELIVERY_FAILURE, "Ugyldig CPA")))
        coEvery { cpaRepoClient.postValidate(any(), any()) } returns invalidResult

        val exception = assertThrows<EbmsException> {
            runBlocking { service.validateIncomingMessage(testAcknowledgment) }
        }
        assertEquals(ErrorCode.DELIVERY_FAILURE, exception.feil.first().code)
    }

    @Test
    fun `validateOutgoingMessage - returns result without signature check`(): Unit = runBlocking {
        coEvery { cpaRepoClient.postValidate(any(), any()) } returns validValidationResult

        val result = service.validateOutgoingMessage(testAcknowledgment)

        assertNotNull(result)
    }
}
