package no.nav.emottak.smtp

import io.ktor.http.*
import jakarta.mail.internet.MimeUtility
import org.junit.jupiter.api.Test

val testHeaderValue = """multipart/related;
	boundary="------=_part_f14474e0_7fda_4a15_b649_87dc04fb39f8"; charset=utf-8;
	start="<soap-c5a5690b-6a9b-4d0a-b50e-8a636948ed13@eik.no>"; type="text/xml""""
class HeaderTest {

    @Test
    fun testHeader() {

        val headers = Headers.build {
            append(MimeHeaders.CONTENT_TYPE, MimeUtility.unfold(testHeaderValue))
        }
        println(headers)
    }
}