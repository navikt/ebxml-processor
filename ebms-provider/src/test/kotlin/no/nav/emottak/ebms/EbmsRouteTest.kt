package no.nav.emottak.ebms

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.emottak.ebms.ebxml.errorList
import no.nav.emottak.ebms.ebxml.messageHeader
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.ebms.validation.MimeHeaders
import no.nav.emottak.ebms.validation.getSignatureDetailsForTest
import no.nav.emottak.ebms.xml.xmlMarshaller
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.Feil
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.Processing
import no.nav.emottak.melding.model.SignatureDetails
import no.nav.emottak.melding.model.ValidationResult
import no.nav.emottak.util.decodeBase64
import org.apache.xml.security.algorithms.MessageDigestAlgorithm
import org.apache.xml.security.signature.XMLSignature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ErrorList
import org.xmlsoap.schemas.soap.envelope.Envelope
import org.xmlsoap.schemas.soap.envelope.Fault
import javax.xml.bind.JAXBElement


class EbmsRouteTest {


    val validMultipartRequest = validMultipartRequest()
    val cpaRepoClient = mockk<CpaRepoClient>()


    fun <T> validationTestApp(testBlock: suspend ApplicationTestBuilder.() -> T) = testApplication {
        val client = createClient{
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        val cpaRepoClient = CpaRepoClient { client }
        application {

            val dokumentValidator = DokumentValidator(cpaRepoClient)
            val processingService = mockk<ProcessingService>()
            routing {
                postEbms(dokumentValidator,processingService,cpaRepoClient)
            }

        }
        externalServices {
                hosts("http://cpa-repo") {
                    this.install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                        json()
                    }
                    routing {
                        post("cpa/validate/soapId-6ae68a32-8b0e-4de2-baad-f4d841aacce1") {
                            call.respond( ValidationResult(null, listOf(Feil(ErrorCode.SECURITY_FAILURE,"Signature Fail"))))
                        }
                        post("cpa/validate/contentID-validRequest") {
                            call.respond(ValidationResult(Processing(mockSignatureDetails(), byteArrayOf())))
                        }
                    }
                }
        }
        testBlock()
    }

    fun <T> mimeTestApp(testBlock: suspend ApplicationTestBuilder.() -> T) = testApplication {


        application {

            val dokumentValidator = DokumentValidator(cpaRepoClient)
            val processingService = mockk<ProcessingService>()
            routing {
                postEbms(dokumentValidator,processingService,cpaRepoClient)
            }

        }
        externalServices {

        }
        testBlock()
    }




    @Test
    fun `Soap Fault om Mime Feil`() = mimeTestApp {

        val wrongMime = validMultipartRequest.modify {
            it.remove(MimeHeaders.MIME_VERSION)
        }

        var response = client.post("/ebms",wrongMime.asHttpRequest())
        var envelope:Envelope =  xmlMarshaller.unmarshal(response.bodyAsText(),Envelope::class.java)
        with (envelope.assertFaultAndGet()) {
            assertEquals("MIME version is missing or incorrect", this.faultstring)
            assertEquals("Server", this.faultcode.localPart)
        }

        val wrongHeader = validMultipartRequest.modify(validMultipartRequest.parts.first() to validMultipartRequest.parts.first().modify {
            it.remove(MimeHeaders.CONTENT_TRANSFER_ENCODING)
        })
        response = client.post("/ebms", wrongHeader.asHttpRequest())
        envelope = xmlMarshaller.unmarshal(response.bodyAsText(),Envelope::class.java)
        with (envelope.assertFaultAndGet()) {
             assertEquals("Mandatory header Content-Transfer-Encoding is undefined", this.faultstring)
             assertEquals("Server", this.faultcode.localPart)
        }
        println(envelope)


    }

    @Test
    fun `Sending unparsable xml as dokument should Soap Fault`()  = mimeTestApp {

         val illegalContent = validMultipartRequest.modify(validMultipartRequest.parts.first() to validMultipartRequest.parts.first().payload("Illegal payload"))

                val response = client.post("/ebms",illegalContent.asHttpRequest())
                val envelope =  xmlMarshaller.unmarshal(response.bodyAsText(),Envelope::class.java)
                with(envelope.assertFaultAndGet()) {
                    assertEquals("Unable to transform request into EbmsDokument: Invalid byte 1 of 1-byte UTF-8 sequence.", this.faultstring)
                    assertEquals("Server", this.faultcode.localPart)
                }

    }

    @Test
    fun `Sending valid request should trigger validation`() = mimeTestApp {
        val validationResult = ValidationResult(null, listOf(Feil(ErrorCode.SECURITY_FAILURE,"Signature Fail")))
        coEvery {
            cpaRepoClient.postValidate(any(),any())
        } returns validationResult
       
        val response = client.post("/ebms",validMultipartRequest.asHttpRequest())
        val envelope =  xmlMarshaller.unmarshal(response.bodyAsText(),Envelope::class.java)
        with(envelope.assertErrorAndGet().error.first()) {
            assertEquals("Signature Fail" , this.description.value)
            assertEquals(ErrorCode.SECURITY_FAILURE.value,this.errorCode)
        }
    }

    @Test
    fun `Not valid request should answer with Feil Signal`() = mimeTestApp {
        val validationResult = ValidationResult(null, listOf(Feil(ErrorCode.SECURITY_FAILURE,"Signature Fail")))
        coEvery {
            cpaRepoClient.postValidate(any(),any())
        } returns validationResult

        val response = client.post("/ebms",validMultipartRequest.asHttpRequest())
        val envelope =  xmlMarshaller.unmarshal(response.bodyAsText(),Envelope::class.java)
        with(envelope.assertErrorAndGet().error.first()) {
            assertEquals("Signature Fail" , this.description.value)
            assertEquals(ErrorCode.SECURITY_FAILURE.value,this.errorCode)
        }

    }

    @Test
    fun `Feil på signature should answer with Feil Signal`() = validationTestApp {
        val response = client.post("/ebms",validMultipartRequest.asHttpRequest())
        val envelope =  xmlMarshaller.unmarshal(response.bodyAsText(),Envelope::class.java)
        with(envelope.assertErrorAndGet().error.first()) {
            assertEquals("Signature Fail" , this.description.value)
            assertEquals(ErrorCode.SECURITY_FAILURE.value,this.errorCode)
        }
    }

    @Test
    fun `If Valid then processing should be triggered`() = validationTestApp {
        val multipart = validMultipartRequest.modify(validMultipartRequest.parts.first() to validMultipartRequest.parts.first().modify {
            it.remove(MimeHeaders.CONTENT_ID)
            it.append(MimeHeaders.CONTENT_ID,"<contentID-validRequest>")
        })
        val response = client.post("/ebms",multipart.asHttpRequest())
        assertEquals(HttpStatusCode.InternalServerError,response.status)
        assertEquals(response.bodyAsText(),"Feil ved prosessering av melding")
    }


    fun Envelope.assertErrorAndGet(): ErrorList {
        assertNotNull(this.header.messageHeader())
        assertNotNull(this.header.errorList())
        return this.header.errorList()!!
    }

    fun Envelope.assertFaultAndGet(): Fault =
        this.body.any.first()
            .let {
                assertTrue(it is JAXBElement<*>)
                it as JAXBElement<*>
            }.let {
                assertTrue( it.value is Fault)
                it.value as Fault
            }

    fun mockSignatureDetails(): SignatureDetails =
    SignatureDetails(
        certificate = decodeBase64("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".toByteArray()),
        signatureAlgorithm = XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
        hashFunction = MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256
    )


}



