package no.nav.emottak.smtp;


import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Store
import jakarta.mail.internet.MimeMultipart
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers

data class EmailMsg(val headers: Map<String,String>, val bytes: ByteArray)

class MailReader(private val store: Store) {

    companion object {
        fun mapEmailMsg(): (Message) -> EmailMsg = {
        message ->
                    EmailMsg(
                        message.allHeaders.toList().groupBy( {it.name}, {it.value} ).mapValues { it.value.joinToString(",") },
                        message.inputStream.readAllBytes()
                    )
    }
    }
    val takeN = 1
    var start = 1

    @Throws(Exception::class)
    fun readMail(expunge:Boolean = true): List<EmailMsg> {
        try {
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            val messageCount = inbox.messageCount

            log.info("Found $messageCount messages")
            val emailMsgList = if (messageCount != 0) {
                val endIndex = takeN.takeIf { start + takeN <= messageCount } ?: messageCount
                val resultat = inbox.getMessages(start, endIndex).toList().onEach {
                    if (it.content is MimeMultipart) {
                        val dokument = runCatching {
                            (it.content as MimeMultipart).getBodyPart(0)
                        }.onSuccess {
                            log.info("Incoming multipart request with headers ${it.allHeaders.toList().map { it.name + ":" + it.value }}" +
                            "with body ${String(it.inputStream.readAllBytes())}")
                        }
                    } else {
                        log.info("Incoming singlepart request ${String(it.inputStream.readAllBytes())}")
                    }
                    val headerXMailer = it.getHeader("X-Mailer")?.toList()?.firstOrNull()
                    log.info(createHeaderMarker(headerXMailer), "From: <${it.from[0]}> Subject: <${it.subject}>")
                    it.setFlag(Flags.Flag.DELETED,expunge)
                }
                start += takeN
                resultat.map (mapEmailMsg())
            }
            else {
                emptyList()
            }
            inbox.close(expunge)
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