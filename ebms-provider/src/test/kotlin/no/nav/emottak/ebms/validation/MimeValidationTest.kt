package no.nav.emottak.ebms.validation

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.util.reflect.*
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class MimeValidationTest {

    val MULTIPART_CONTENT_TYPE: String = """multipart/related;type="text/xml";boundary="----=_Part_495_-1172936255.1665395092859";start="<soapId-6ae68a32-8b0e-4de2-baad-f4d841aacce1>";"""

    val valid = Headers.build {
        append(MimeHeaders.MIME_VERSION,"1.0")
        append(MimeHeaders.SOAP_ACTION,"ebXML")
        append(MimeHeaders.CONTENT_TYPE,MULTIPART_CONTENT_TYPE)
    }

    val validSoapMimeHeaders =  Headers.build {
        append(MimeHeaders.CONTENT_ID,"<soapId-6ae68a32-8b0e-4de2-baad-f4d841aacce1>")
        append(MimeHeaders.CONTENT_TRANSFER_ENCODING,"base64")
        append(MimeHeaders.CONTENT_TYPE, """text/xml; charset="UTF-8"""")
    }

    val validSoapAttachmentHeaders =  Headers.build {
        append(MimeHeaders.CONTENT_ID,"<soapId-6ae68a32-8b0e-4de2-baad-f4d841aacce1>")
        append(MimeHeaders.CONTENT_TRANSFER_ENCODING,"base64")
        append(MimeHeaders.CONTENT_DISPOSITION,"attachment")
        append(MimeHeaders.CONTENT_TYPE, """application/pkcs7-mime; smimetype=enveloped-data"""")
    }

    private fun Headers.modify(block: (HeadersBuilder) -> Unit) = Headers.build {
        this@modify.entries().forEach {
            this.append(it.key, it.value.first())
            this.apply(block)
        }
    }

    private fun Headers.mockApplicationRequest() : ApplicationRequest {
       val appRquest = mockk<ApplicationRequest>()
        every {
            appRquest.headers
        } returns this@mockApplicationRequest

        every { appRquest.contentType() } returns this@mockApplicationRequest[MimeHeaders.CONTENT_TYPE].takeUnless { it == null}.let {
            if (it == null) ContentType.Any else ContentType.parse(it)
        }

        return appRquest
    }

    @BeforeEach
    fun setup() {
        clearAllMocks()
        mockkStatic(ApplicationRequest::validateMime)
        mockkStatic(ApplicationRequest::contentType)
    }

    @Test
    fun `5-2-2-1 When all headers are valid`() {
        val applicationRequest = valid.mockApplicationRequest()

        applicationRequest.validateMime()

    }


    @Test
    fun `5-2-2-1 Content type is text xml`() {
        val appRequest = valid.modify {
            it[MimeHeaders.CONTENT_TYPE] = "text/xml"
        }.mockApplicationRequest()

        appRequest.validateMime()
    }
    @Test
    fun `5-2-2-1 Mime versjon er feil`() {

        val notValid = listOf(
            valid.modify { it.remove(MimeHeaders.MIME_VERSION) },
            valid.modify { it[MimeHeaders.MIME_VERSION] = "2.1" },
            valid.modify { it.remove(MimeHeaders.SOAP_ACTION)},
            valid.modify { it[MimeHeaders.SOAP_ACTION] = "noeannet" },
            valid.modify { it.remove(MimeHeaders.CONTENT_TYPE)}
        )


       notValid.map { it.mockApplicationRequest() }.forEach {

           runCatching {

               it.validateMime()
           }.onFailure {
               it.printStackTrace()
               assert(it.instanceOf(MimeValidationException::class))
           }.onSuccess {
               fail { "Should have failed" }
           }
       }
    }

    @Test
    fun `5-2-2-2 endre rekkefølge på multipart attributene`() {
        valid.modify {
            it[MimeHeaders.CONTENT_TYPE] = """multipart/related;type="text/xml";start="<soapId-6ae68a32-8b0e-4de2-baad-f4d841aacce1>";boundary="----=_Part_495_-1172936255.1665395092859""""
        }
            .mockApplicationRequest()
            .validateMime()

    }

    @Test
    fun `5-2-2-2 illegal type`() {

        val notValid = listOf(
            valid.modify { it[MimeHeaders.CONTENT_TYPE] = """multipart/unrelated;type="text/xml";start="<soapId-6ae68a32-8b0e-4de2-baad-f4d841aacce1>";boundary="----=_Part_495_-1172936255.1665395092859"""" },
            valid.modify { it[MimeHeaders.CONTENT_TYPE] = """multipart/related;type="ascii/xml";start="<soapId-6ae68a32-8b0e-4de2-baad-f4d841aacce1>";boundary="----=_Part_495_-1172936255.1665395092859"""" },
            valid.modify { it[MimeHeaders.CONTENT_TYPE] = """multipart/related;type="text/xml";start="<soapId-6ae68a32-8b0e-4de2-baad-f4d841aacce1>""""},
            valid.modify { it[MimeHeaders.CONTENT_TYPE] = """multipart/unrelated;type="text/xml";boundary="----=_Part_495_-1172936255.1665395092859"""" },
            valid.modify { it.remove(MimeHeaders.CONTENT_TYPE)}
        )

        notValid.map { it.mockApplicationRequest() }.forEach {
            runCatching {
                it.validateMime()
            }.onFailure {
                assert(it.instanceOf(MimeValidationException::class))
            }.onSuccess {
                fail { "Should have failed" }
            }
        }
    }

    @Test
    fun `5-2-2-3 validering på SOAP envelope multipart`() {

        PartData.FormItem("body",{},validSoapMimeHeaders).validateMimeSoapEnvelope()

    }

    @Test
    fun `5-2-2-3 feil på validering på SOAP envelope multipart`() {
        val notValid = listOf(
            validSoapMimeHeaders.modify {
                it.remove(MimeHeaders.CONTENT_ID)
            },
            validSoapMimeHeaders.modify {
                it[MimeHeaders.CONTENT_TRANSFER_ENCODING] = "noeannet"
            },
            validSoapMimeHeaders.modify {
                it[MimeHeaders.CONTENT_TYPE] = "text/flat"
            },
            validSoapMimeHeaders.modify {
                it.remove(MimeHeaders.CONTENT_TYPE)
            },
        )

        notValid.map { it.mockApplicationRequest() }.forEach {
            runCatching {
                PartData.FormItem("body", {}, it.headers).validateMimeSoapEnvelope()
            }
                .onFailure {
                    assert(it.instanceOf(MimeValidationException::class))
                }.onSuccess {
                    fail { "Should have failed" }
                }
        }
    }

    @Test
    fun `5-2-2-4 validering på SOAP envelope multipart`() {

        PartData.FormItem("body",{},validSoapAttachmentHeaders).validateMimeAttachment()

    }

    @Test
    fun `5-2-2-4 validering på SOAP attachment multipart`() {
        val notValid = listOf(
            validSoapMimeHeaders.modify {
                it.remove(MimeHeaders.CONTENT_ID)
            },
            validSoapMimeHeaders.modify {
                it[MimeHeaders.CONTENT_TRANSFER_ENCODING] = "noeannet"
            },
            validSoapMimeHeaders.modify {
                it[MimeHeaders.CONTENT_TYPE] = "text/flat"
            },
            validSoapMimeHeaders.modify {
                it.remove(MimeHeaders.CONTENT_TYPE)
            },
        )

        notValid.map { it.mockApplicationRequest() }.forEach {
            runCatching {
                PartData.FormItem("body", {}, it.headers).validateMimeSoapEnvelope()
            }
                .onFailure {
                    assert(it.instanceOf(MimeValidationException::class))
                }.onSuccess {
                    fail { "Should have failed" }
                }

        }
    }

}