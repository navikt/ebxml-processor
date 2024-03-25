package no.nav.emottak.smtp

import io.ktor.http.Headers
import io.mockk.every
import io.mockk.mockk
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeUtility
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals

val testHeaderValue = """multipart/related;
	boundary="------=_part_f14474e0_7fda_4a15_b649_87dc04fb39f8"; charset=utf-8;
	start="<soap-c5a5690b-6a9b-4d0a-b50e-8a636948ed13@eik.no>"; type="text/xml""""
class MessageTest {

    fun mockSession(): Session {
        val properties = Properties().also { props ->
            props["mail.pop3.socketFactory.fallback"] = "false"
            props["mail.pop3.socketFactory.port"] = getEnvVar("SMTP_POP3_FACTORY_PORT", "3110")
            props["mail.pop3.port"] = getEnvVar("SMTP_POP3_PORT", "3110")
            props["mail.pop3.host"] = getEnvVar("SMTP_POP3_HOST", "localhost")
            props["mail.store.protocol"] = getEnvVar("SMTP_STORE_PROTOCOL", "pop3")
        }

        return Session.getDefaultInstance(properties)
    }
    fun mockStore(msg: Message): Store {
        val store = mockk<Store>(relaxed = true)
        val inbox = mockk<Folder>(relaxed = true)
        every {
            store.getFolder("INBOX")
        } returns inbox

        every {
            inbox.messageCount
        } returns 1
        every {
            inbox.getMessages(1, 1)
        } returns arrayOf(msg)

        return store
    }

    @Test
    fun testHeader() {
        val headers = Headers.build {
            append(MimeHeaders.CONTENT_TYPE, MimeUtility.unfold(testHeaderValue))
        }
        println(headers)
    }

    @Test
    fun `Ta en melding`() {
        val session = mockSession()
        val stream = this.javaClass.classLoader.getResourceAsStream("mails/nyebmstest@test-es.nav.no/INBOX/example.eml")
        val msg = MimeMessage(session, stream)
        val store = mockStore(msg)
        val reader = MailReader(store)
        val one = MailReader.mapEmailMsg().invoke(msg)
        val two = reader.readMail().first()
        assertEquals(one.headers, two.headers)
        assertEquals(String(one.parts.first().bytes), String(two.parts.first().bytes))
    }

    @Test
    fun `Delete in batches`() {
        val session = mockSession()
        val stream = this.javaClass.classLoader.getResourceAsStream("mails/nyebmstest@test-es.nav.no/INBOX/example.eml")
        val msg = MimeMessage(session, stream)
        val store = mockStore(msg)
        store.getFolder("INBOX").batchDelete(100)
    }
}
