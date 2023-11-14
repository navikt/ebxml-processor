package no.nav.emottak.smtp;


import jakarta.mail.Authenticator
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Store
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import no.nav.emottak.util.getEnvVar
import java.util.Properties


val properties = Properties().also { props ->
    props["mail.pop3.socketFactory.fallback"] = "false"
    props["mail.pop3.socketFactory.port"] = getEnvVar("SMTP_POP3_FACTORY_PORT", "3110")
    props["mail.pop3.port"] = getEnvVar("SMTP_POP3_PORT", "3110")
    props["mail.pop3.host"] = getEnvVar("SMTP_POP3_HOST", "localhost")
    props["mail.store.protocol"] = getEnvVar("SMTP_STORE_PROTOCOL", "pop3")
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
    fun readMail(): List<Message> {
        try {
            store.connect()
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY) //TODO READ_WRITE
            val messages = inbox.messages
            log.info("Found ${messages.size} messages")
            for (message in messages) {
                val from = message.from[0]
                val subject = message.subject
                val headerXMailer = message.getHeader("X-Mailer")?.toList()?.firstOrNull()
                //log.info(String(message.inputStream.readAllBytes()))
                log.info(createHeaderMarker(headerXMailer), "From: <$from> Subject: <$subject>")
            }
            inbox.close(false) //TODO true
            store.close()
            return messages.toList()
        } catch (e: Exception) {
            log.error("Error connecting to mail server", e)
            throw e
        }
    }

    private fun createHeaderMarker(xMailer: String?): LogstashMarker? {
        val map = mutableMapOf<String,String>()
        map["systemkilde"] = xMailer ?: "-"
        return Markers.appendEntries(map)
    }

//    private fun Message.createHeaderMarker(): LogstashMarker {
//        val headerMap = mutableMapOf<String,String>()
//        this.allHeaders.iterator().forEach {
//            headerMap[it.name] = it.value
//        }
//        return Markers.appendEntries(headerMap)
//    }
}
