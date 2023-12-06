package no.nav.emottak.smtp

import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.user.GreenMailUser
import com.icegreen.greenmail.util.ServerSetupTest
import jakarta.mail.Store
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.test.assertEquals


class GreenmailIT {

    @RegisterExtension
    var greenMail: GreenMailExtension = GreenMailExtension(ServerSetupTest.SMTP_POP3)

    fun mockStore() : Store {
        val user: GreenMailUser = greenMail.setUser("nyebmstest@test-es.nav.no","nyebmstest@test-es.nav.no","test1234")
        greenMail.loadEmails(Path.of(this::class.java.classLoader.getResource("mails").toURI()));
        val store = greenMail.pop3.createStore()
        store.connect("nyebmstest@test-es.nav.no","test1234")
        return store
    }

    @Test
    fun `Mail readeren lesser riktig test inbox`() {
        val store = mockStore()
        val reader = MailReader(store, false)
        assertEquals(1, reader.routeMail().let { it.first + it.second })
    }


}