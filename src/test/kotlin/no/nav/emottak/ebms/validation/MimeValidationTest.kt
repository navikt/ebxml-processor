package no.nav.emottak.ebms.validation

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.content.PartData
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.contentType
import io.ktor.util.reflect.instanceOf
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.emottak.ebms.modify
import no.nav.emottak.ebms.valid
import no.nav.emottak.ebms.validSoapAttachmentHeaders
import no.nav.emottak.ebms.validSoapMimeHeaders
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class MimeValidationTest {

    private fun Headers.mockApplicationRequest(): ApplicationRequest {
        val appRquest = mockk<ApplicationRequest>()
        every {
            appRquest.headers
        } returns this@mockApplicationRequest

        every { appRquest.contentType() } returns this@mockApplicationRequest[MimeHeaders.CONTENT_TYPE].takeUnless { it == null }.let {
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
            valid.modify { it.remove(MimeHeaders.CONTENT_TYPE) }
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
            valid.modify { it[MimeHeaders.CONTENT_TYPE] = """multipart/related;type="text/xml";start="<soapId-6ae68a32-8b0e-4de2-baad-f4d841aacce1>"""" },
            valid.modify { it[MimeHeaders.CONTENT_TYPE] = """multipart/unrelated;type="text/xml";boundary="----=_Part_495_-1172936255.1665395092859"""" },
            valid.modify { it.remove(MimeHeaders.CONTENT_TYPE) }
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
        PartData.FormItem("body", {}, validSoapMimeHeaders).validateMimeSoapEnvelope()
    }

    @Test
    fun `5-2-2-3 feil på validering på SOAP envelope multipart`() {
        val notValid = listOf(
            // TODO Kontakt EPJ der Content ID mangler
//            validSoapMimeHeaders.modify {
//                it.remove(MimeHeaders.CONTENT_ID)
//            },
            validSoapMimeHeaders.modify {
                it[MimeHeaders.CONTENT_TRANSFER_ENCODING] = "noeannet"
            },
            validSoapMimeHeaders.modify {
                it[MimeHeaders.CONTENT_TYPE] = "text/flat"
            },
            validSoapMimeHeaders.modify {
                it.remove(MimeHeaders.CONTENT_TYPE)
            }
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
        PartData.FormItem("body", {}, validSoapAttachmentHeaders).validateMimeAttachment()
    }

    @Test
    fun `5-2-2-4 validering på SOAP attachment multipart`() {
        val notValid = listOf(
            // TODO Kontakt EPJ der Content ID mangler
//            validSoapMimeHeaders.modify {
//                it.remove(MimeHeaders.CONTENT_ID)
//            },
            validSoapMimeHeaders.modify {
                it[MimeHeaders.CONTENT_TRANSFER_ENCODING] = "noeannet"
            },
            validSoapMimeHeaders.modify {
                it[MimeHeaders.CONTENT_TYPE] = "text/flat"
            },
            validSoapMimeHeaders.modify {
                it.remove(MimeHeaders.CONTENT_TYPE)
            }
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
