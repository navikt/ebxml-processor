package no.nav.emottak.smtp;


import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Store
import jakarta.mail.internet.InternetHeaders
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.internet.MimeUtility
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import no.nav.emottak.util.getEnvVar

data class EmailMsg(val headers: Map<String, String>, val bytes: ByteArray)

class MailReader(store: Store, val expunge: Boolean = true) : AutoCloseable {

    val inbox: Folder = store.getFolder("INBOX")

    init {
        inbox.open(Folder.READ_WRITE)
    }

    companion object {
        fun mapEmailMsg(): (MimeMessage) -> EmailMsg = { message ->
            EmailMsg(
                message.allHeaders.toList().groupBy({ it.name }, { it.value }).mapValues { it.value.joinToString(",") },
                message.rawInputStream.readAllBytes()
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
        inbox.close(expunge().also {
            if (expunge != it)
                log.warn("Inbox limit [$inboxLimit] exceeded. Expunge forced $it")
        })
    }


    fun unfoldMimeMultipartHeaders(input: MimeMultipart) {
        for (i in 0 until input.count) {
            input.getBodyPart(i)
                .allHeaders.toList()
                .forEach { header ->
                    input.getBodyPart(i).setHeader(header.name,MimeUtility.unfold(header.value))
                }
        }
    }

    private fun logMessage(mimeMessage: MimeMessage) {
        if (mimeMessage.content is MimeMultipart) {
                        runCatching {
                            (mimeMessage.content as MimeMultipart).getBodyPart(0)
                        }.onSuccess {
                            log.info(
                                "Incoming multipart request with headers ${mimeMessage.allHeaders.toList().map { it.name + ":" + it.value }} part headers ${
                                    it.allHeaders.toList().map { it.name + ":" + it.value }
                                }" +
                                        "with body ${String(it.inputStream.readAllBytes())}"
                            )
                        }
        }
    }

    @Throws(Exception::class)
    fun readMail(): List<EmailMsg> {
        try {
            val messageCount = inbox.messageCount
            val emailMsgList = if (messageCount != 0) {
                val endIndex = (takeN + start - 1).takeIf { it < messageCount } ?: messageCount
                val resultat: List<MimeMessage> = inbox.getMessages(start, endIndex)
                    .map {
                        it.setFlag(Flags.Flag.DELETED, expunge())
                        if (it.content is MimeMultipart) {
                           unfoldMimeMultipartHeaders(it.content as MimeMultipart)
                        }
                        it as MimeMessage
                    }.onEach (::logMessage)
                start += takeN
                resultat.map(mapEmailMsg())
            } else {
                log.info("Fant ikke noe eposter")
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
    
}