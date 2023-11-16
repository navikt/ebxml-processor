package no.nav.emottak.smtp;


import jakarta.mail.Authenticator
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Store
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import no.nav.emottak.util.getEnvVar
import java.util.Properties



class MailReader(store: Store)
 {
    val takeN = 1


    @Throws(Exception::class)
    fun readMail(): List<Message> {
        try {
            store.connect()
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            val messages = inbox.messages

            log.info("Found ${messages.size} messages")
            val endIndex = takeN.takeIf { takeN < messages.size } ?: messages.size
            val resultat = inbox.getMessages(1,endIndex).toList().onEach {
                 val from = it.from
                 val subject = it.subject
                 val headerXMailer = it.getHeader("X-Mailer")?.toList()?.firstOrNull()
                 log.info(createHeaderMarker(headerXMailer), "From: <$from> Subject: <$subject>")
            }

            inbox.close(true)
            store.close()
            return resultat
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
