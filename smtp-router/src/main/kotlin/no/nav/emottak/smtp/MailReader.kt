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
    fun readMail(): String {
        try {
            store.connect()
            val inbox = store.getFolder("INBOX")
            inbox.open(jakarta.mail.Folder.READ_ONLY)
            val messages = inbox.getMessages()
            log.info("Found ${messages.size} messages")
            for (message in messages) {
//                log.info(message.createHeaderMarker(), "From: <${message.from[0]}> Subject: ${message.subject}")
                log.info("From: <${message.from[0]}> Subject: <${message.subject}> X-Mailer: <${message.getHeader("X-Mailer")?.toList()}>")
//                log.info("Header names: ${message.allHeaders.toList().map { it.name }.toList()}")
                //TODO Logg x-mailer header for identifikasjon av samhandlingssystem
            }
            inbox.close(false)
            store.close()
            return "Found ${messages.size} messages"
        } catch (e: Exception) {
            log.error("Error connecting to mail server", e)
            throw e
        }
    }

//    private fun Message.createHeaderMarker(): LogstashMarker {
//        val headerMap = mutableMapOf<String,String>()
//        this.allHeaders.iterator().forEach {
//            headerMap[it.name] = it.value
//        }
//        return Markers.appendEntries(headerMap)
//    }
}
