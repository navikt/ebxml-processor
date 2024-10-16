package no.nav.emottak.ebms

import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import no.nav.emottak.ebms.ebxml.errorList
import no.nav.emottak.ebms.ebxml.messageHeader
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.ebms.validation.MimeHeaders
import no.nav.emottak.ebms.xml.xmlMarshaller
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Feil
import no.nav.emottak.message.model.PayloadProcessing
import no.nav.emottak.message.model.ProcessConfig
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.message.model.ValidationResult
import no.nav.emottak.util.decodeBase64
import no.nav.emottak.util.getEnvVar
import org.apache.xml.security.algorithms.MessageDigestAlgorithm
import org.apache.xml.security.signature.XMLSignature
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ErrorList
import org.xmlsoap.schemas.soap.envelope.Envelope

abstract class EbmsRoutFellesIT(val endpoint: String) {

    val validMultipartRequest = validMultipartRequest()
    val processingService = mockk<ProcessingService>()
    val mockProcessConfig = ProcessConfig(
        true,
        true,
        true,
        true,
        true,
        true,
        false,
        false,
        "HarBorgerFrikort",
        null
    )

    fun <T> validationTestApp(testBlock: suspend ApplicationTestBuilder.() -> T) = testApplication {
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        val cpaRepoClient = CpaRepoClient { client }
        val sendInClient = SendInClient { client }
        application {
            val dokumentValidator = DokumentValidator(cpaRepoClient)

            coEvery {
                processingService.processAsync(any(), any())
            } just runs
            routing {
                postEbmsSync(dokumentValidator, processingService, SendInService(sendInClient))
                postEbmsAsync(dokumentValidator, processingService)
            }
        }
        externalServices {
            hosts(getEnvVar("CPA_REPO_URL", "http://cpa-repo.team-emottak.svc.nais.local")) {
                this.install(ContentNegotiation) {
                    json()
                }
                routing {
                    post("cpa/validate/soapId-6ae68a32-8b0e-4de2-baad-f4d841aacce1") {
                        call.respond(ValidationResult(error = listOf(Feil(ErrorCode.SECURITY_FAILURE, "Signature Fail"))))
                    }
                    post("cpa/validate/contentID-validRequest") {
                        call.respond(ValidationResult(payloadProcessing = PayloadProcessing(mockSignatureDetails(), byteArrayOf(), mockProcessConfig)))
                    }
                }
            }
        }
        testBlock()
    }

    @Test
    fun `Feil på signature should answer with Feil Signal`() = validationTestApp {
        val response = client.post("/ebms/async", validMultipartRequest.asHttpRequest())
        val envelope = xmlMarshaller.unmarshal(response.bodyAsText(), Envelope::class.java)
        with(envelope.assertErrorAndGet().error.first()) {
            Assertions.assertEquals("Signature Fail", this.description.value)
            Assertions.assertEquals(
                ErrorCode.SECURITY_FAILURE.value,
                this.errorCode
            )
        }
    }

    @Test
    fun `Valid payload request should trigger processing`() = validationTestApp {
        val multipart = validMultipartRequest.modify(
            validMultipartRequest.parts.first() to validMultipartRequest.parts.first().modify {
                it.remove(MimeHeaders.CONTENT_ID)
                it.append(MimeHeaders.CONTENT_ID, "<contentID-validRequest>")
            }
        )
        client.post("/ebms/async", multipart.asHttpRequest())
        coVerify(exactly = 1) {
            processingService.processAsync(any(), any())
        }
    }
}

fun mockSignatureDetails(): SignatureDetails =
    SignatureDetails(
        certificate = decodeBase64("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".toByteArray()),
        signatureAlgorithm = XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
        hashFunction = MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256
    )
fun Envelope.assertErrorAndGet(): ErrorList {
    Assertions.assertNotNull(this.header.messageHeader())
    Assertions.assertNotNull(this.header.errorList())
    return this.header.errorList()!!
}
