package no.nav.emottak.ebms.validation

import io.ktor.http.*
import io.ktor.util.reflect.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class MimeValidationTest {

    val valid = Headers.build {
        append(MimeHeaders.MIME_VERSION,"1.0")
        append(MimeHeaders.SOAP_ACTION,"ebXML")
        append(MimeHeaders.CONTENT_TYPE,"""multipart/related;type="text/xml";boundary="----=_Part_495_-1172936255.1665395092859";start="<soapId-6ae68a32-8b0e-4de2-baad-f4d841aacce1>";""")
    }

    private fun Headers.modify(block: (HeadersBuilder) -> Unit) = Headers.build {
        this@modify.entries().forEach {
            this.append(it.key, it.value.first())
            this.apply(block)
        }
    }

    @Test
    fun `5-2-2-1 When all headers are valid`() {

        valid.validateMime()

    }

    @Test
    fun `5-2-2-1 Content type is text xml`() {
        val headers = valid.modify {
            it[MimeHeaders.CONTENT_TYPE] = "text/xml"
        }
        headers.validateMime()
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
       notValid.forEach {
           runCatching {
               it.validateMime()
           }.onFailure {
               assert(it.instanceOf(MimeValidationException::class))
           }.onSuccess {
               fail { "Should have failed" }
           }
       }



    }

}