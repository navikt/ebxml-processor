package no.nav.emottak.smtp;


import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Store
import no.nav.emottak.util.getEnvVar
import java.util.Properties


val properties = Properties().let { props ->
    props["mail.pop3.socketFactory.fallback"] = "false"
    props["mail.pop3.socketFactory.port"] = getEnvVar("SMTP_POP3_FACTORY_PORT", "3110")
    props["mail.pop3.port"] = getEnvVar("SMTP_POP3_PORT", "3110")
    props["mail.pop3.host"] = getEnvVar("SMTP_POP3_HOST", "localhost")
    props["mail.store.protocol"] = getEnvVar("SMTP_STORE_PROTOCOL", "pop3")
    props
}

val smtpUsername = getEnvVar("SMTP_USERNAME", "test@test.test")
val smtpPassword = getEnvVar("SMTP_PASSWORD", "changeit")

class MailReader(
    props: Properties = properties,
    val username: String = smtpUsername,
    val password: String = smtpPassword
) {
    private val store: Store

    init {
        val auth = object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
        }
        val session = Session.getDefaultInstance(props, auth)
        store = session.getStore("pop3")
    }

    @Throws(Exception::class)
    fun readMail(): List<String> {
        store.connect()
        val inbox = store.getFolder("INBOX")
        inbox.open(jakarta.mail.Folder.READ_ONLY)
        val messages = inbox.getMessages()
        val messageSubjects = mutableListOf<String>()
        for (message in messages) {
            log.info("Message found in inbox")
            log.info("Subject: " + message.subject)
            log.info("From: " + message.from[0])
            log.info("Text: " + message.content.toString())
            messageSubjects.add(message.subject)
        }
        inbox.close(false)
        store.close()
        return messageSubjects
    }
}
