package no.nav.emottak.ebms.validation

import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.emottak.cpa.decodeBase64Mime
import no.nav.emottak.ebms.CpaRepoClient
import no.nav.emottak.ebms.asHttpRequest
import no.nav.emottak.ebms.modify
import no.nav.emottak.ebms.payload
import no.nav.emottak.ebms.postEbmsSync
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.util.EventRegistrationServiceFake
import no.nav.emottak.ebms.validMultipartRequest
import no.nav.emottak.message.ebxml.errorList
import no.nav.emottak.message.ebxml.messageHeader
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Feil
import no.nav.emottak.message.model.ValidationResult
import no.nav.emottak.message.xml.xmlMarshaller
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ErrorList
import org.xmlsoap.schemas.soap.envelope.Envelope
import org.xmlsoap.schemas.soap.envelope.Fault
import javax.xml.bind.JAXBElement

class MimeValidationIT {

    val validMultipartRequest = validMultipartRequest()
    val cpaRepoClient = mockk<CpaRepoClient>()
    val eventRegistrationService = EventRegistrationServiceFake()

    fun <T> mimeTestApp(testBlock: suspend ApplicationTestBuilder.() -> T) = testApplication {
        application {
            val cpaValidationService = CPAValidationService(cpaRepoClient)
            val processingService = mockk<ProcessingService>()
            val sendInService = mockk<SendInService>()
            routing {
                postEbmsSync(cpaValidationService, processingService, sendInService, eventRegistrationService)
            }
        }
        externalServices {
        }
        testBlock()
    }

    @Test
    fun `Soap Fault om Mime Feil`() = mimeTestApp {
        val wrongHeader = validMultipartRequest.modify(
            validMultipartRequest.parts.first() to validMultipartRequest.parts.first().modify {
                it.remove(MimeHeaders.CONTENT_TRANSFER_ENCODING)
            }
        )
        val response = client.post("/ebms/sync", wrongHeader.asHttpRequest())
        val responseText = response.bodyAsText()
        println(responseText.decodeBase64Mime())
        val envelope = xmlMarshaller.unmarshal(response.bodyAsText(), Envelope::class.java)
        with(envelope.assertFaultAndGet()) {
            assertEquals(
                "Mandatory header Content-Transfer-Encoding is undefined",
                this.faultstring
            )
            assertEquals("Server", this.faultcode.localPart)
        }
        println(envelope)
    }

    @Test
    fun `Sending unparsable xml as document should Soap Fault`() = mimeTestApp {
        val illegalContent = validMultipartRequest.modify(validMultipartRequest.parts.first() to validMultipartRequest.parts.first().payload("Illegal payload"))

        val response = client.post("/ebms/sync", illegalContent.asHttpRequest())
        val envelope = xmlMarshaller.unmarshal(response.bodyAsText(), Envelope::class.java)
        with(envelope.assertFaultAndGet()) {
            assertEquals(
                "Invalid byte 1 of 1-byte UTF-8 sequence.",
                this.faultstring
            )
            assertEquals("Server", this.faultcode.localPart)
        }
    }

    @Test
    fun `Sending valid request should trigger validation`() = mimeTestApp {
        val validationResult = ValidationResult(error = listOf(Feil(ErrorCode.SECURITY_FAILURE, "Signature Fail")))
        coEvery {
            cpaRepoClient.postValidate(any(), any())
        } returns validationResult

        val response = client.post("/ebms/sync", validMultipartRequest.asHttpRequest())
        val envelope = xmlMarshaller.unmarshal(response.bodyAsText(), Envelope::class.java)
        with(envelope.assertErrorAndGet().error.first()) {
            assertEquals("Signature Fail", this.description?.value)
            assertEquals(ErrorCode.SECURITY_FAILURE.value, this.errorCode)
        }
    }

    @Test
    fun `Not valid request should answer with Feil Signal`() = mimeTestApp {
        val validationResult = ValidationResult(error = listOf(Feil(ErrorCode.SECURITY_FAILURE, "Signature Fail")))
        coEvery {
            cpaRepoClient.postValidate(any(), any())
        } returns validationResult

        val response = client.post("/ebms/sync", validMultipartRequest.asHttpRequest())
        val envelope = xmlMarshaller.unmarshal(response.bodyAsText(), Envelope::class.java)
        with(envelope.assertErrorAndGet().error.first()) {
            assertEquals("Signature Fail", this.description?.value)
            assertEquals(
                ErrorCode.SECURITY_FAILURE.value,
                this.errorCode
            )
        }
    }

    fun Envelope.assertFaultAndGet(): Fault =
        this.body.any!!.first()
            .let {
                assertTrue(it is JAXBElement<*>)
                it as JAXBElement<*>
            }.let {
                assertTrue(it.value is Fault)
                it.value as Fault
            }

    fun Envelope.assertErrorAndGet(): ErrorList {
        assertNotNull(this.header!!.messageHeader())
        assertNotNull(this.header!!.errorList())
        return this.header!!.errorList()!!
    }
}
