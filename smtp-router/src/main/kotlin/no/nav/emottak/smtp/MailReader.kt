package no.nav.emottak.smtp;


import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.headers
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Store
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
data class EmailMsg(val headers: Headers, val bytes: ByteArray)

class MailReader(private val store: Store) {
    val takeN = 1

    @Throws(Exception::class)
    fun readMail(): List<EmailMsg> {
        try {
            store.connect()
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            val messages = inbox.messages

            log.info("Found ${messages.size} messages")
            val emailMsgList = if(messages.isNotEmpty()) {
                val endIndex = takeN.takeIf { takeN <= messages.size } ?: messages.size
                val resultat = inbox.getMessages(1, endIndex).toList().onEach {
                    val from = it.from
                    val subject = it.subject
                    val headerXMailer = it.getHeader("X-Mailer")?.toList()?.firstOrNull()
                    log.info(createHeaderMarker(headerXMailer), "From: <$from> Subject: <$subject>")
                }
                resultat.map {
                    EmailMsg(Headers.build {
                        it.allHeaders.toList().map { append(it.name, it.value) }
                    }, it.inputStream.readAllBytes())
                }
            }
            else {
                emptyList()
            }

            inbox.close(true)
            store.close()
            return emailMsgList
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
