package no.nav.emottak.smtp;


import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Store
import jakarta.mail.internet.MimeMultipart
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import no.nav.emottak.util.getEnvVar
import java.util.Date

data class EmailMsg(val headers: Map<String, String>, val bytes: ByteArray)

class MailReader(private val store: Store, val expunge: Boolean = true): AutoCloseable {

    val inbox: Folder = store.getFolder("INBOX")

    init {
        inbox.open(Folder.READ_WRITE)
    }

    companion object {
        fun mapEmailMsg(): (Message) -> EmailMsg = { message ->
            EmailMsg(
                message.allHeaders.toList().groupBy({ it.name }, { it.value }).mapValues { it.value.joinToString(",") },
                message.inputStream.readAllBytes()
            )
        }
    }

    val takeN = 1
    var start = 1
    val inboxLimit: Int = getEnvVar("INBOX_LIMIT", "2000").toInt()

    fun count() = inbox.messageCount

    fun expunge(): Boolean {
        return (expunge || count() > inboxLimit)
    }

    override fun close() {
        inbox.close(expunge().also { if (expunge != it)
            log.warn("Inbox limit [$inboxLimit] exceeded. Expunge forced $it") })
    }

    @Throws(Exception::class)
    fun readMail(receivedAfter: Date? = null): List<EmailMsg> {
        try {
            val messageCount = inbox.messageCount
            log.info("Found $messageCount messages")
            val emailMsgList = if (messageCount != 0) {
                val endIndex = takeN.takeIf { start + takeN <= messageCount } ?: messageCount
                val resultat = inbox.getMessages(start, endIndex).toList().onEach {
                    message ->
                    if (message.content is MimeMultipart) {
                        val dokument = runCatching {
                            (message.content as MimeMultipart).getBodyPart(0)
                        }.onSuccess {
                            if(receivedAfter?.after(message.receivedDate) == true) {
                                log.info("Incoming multipart request with headers ${
                                        it.allHeaders.toList().map { it.name + ":" + it.value }
                                    }" +
                                            "with body ${String(it.inputStream.readAllBytes())}"
                                )
                            }
                        }
                    } else {
                        log.info("Incoming singlepart request ${String(message.inputStream.readAllBytes())}")
                    }
                    val headerXMailer = message.getHeader("X-Mailer")?.toList()?.firstOrNull()
                    log.info(createHeaderMarker(headerXMailer), "From: <${message.from[0]}> Subject: <${message.subject}>")
                    message.setFlag(Flags.Flag.DELETED, expunge())
                }
                start += takeN
                resultat.map(mapEmailMsg())
            } else {
                emptyList()
            }
            return emailMsgList
        } catch (e: Exception) {
            log.error("Error connecting to mail server", e)
            throw e
        }
    }


    private fun createHeaderMarker(xMailer: String?): LogstashMarker? {
        val map = mutableMapOf<String, String>()
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