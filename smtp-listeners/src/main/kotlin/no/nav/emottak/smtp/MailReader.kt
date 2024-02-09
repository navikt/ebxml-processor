package no.nav.emottak.smtp

import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Store
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers

data class EmailMsg(val headers: Map<String, String>, val parts: List<Part>)
data class Part(val headers: Map<String, String>, val bytes: ByteArray)

class MailReader(private val store: Store, val expunge: Boolean = true) : AutoCloseable {

    val inbox: Folder = store.getFolder("INBOX")

    init {
        inbox.open(Folder.READ_WRITE)
    }

    companion object {
        fun mapEmailMsg(): (MimeMessage) -> EmailMsg = { message ->
            val bodyparts: List<Part> = if (message.content is MimeMultipart) {
                (message.content as MimeMultipart).let {
                    mutableListOf<MimeBodyPart>().apply {
                        for (i in 0 until it.count) {
                            this.add(it.getBodyPart(i) as MimeBodyPart)
                        }
                    }.map(mapBodyPart())
                }
            } else {
                listOf(
                    Part(
                        emptyMap(),
                        message.rawInputStream.readAllBytes()
                    )
                )
            }
            EmailMsg(
                message.allHeaders.toList().groupBy({ it.name }, { it.value }).mapValues { it.value.joinToString(",") },
                bodyparts
            )
        }

        private fun mapBodyPart(): (MimeBodyPart) -> Part = { bodyPart ->
            Part(
                bodyPart.allHeaders.toList().groupBy({ it.name }, { it.value })
                    .mapValues { it.value.joinToString(",") },
                bodyPart.rawInputStream.readAllBytes()
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
        inbox.close(
            expunge().also {
                if (expunge != it) {
                    log.warn("Inbox limit [$inboxLimit] exceeded. Expunge forced $it")
                }
            }
        )
    }

    @Throws(Exception::class)
    fun readMail(): List<EmailMsg> {
        try {
            val messageCount = inbox.messageCount
            val emailMsgList = if (messageCount != 0) {
                val endIndex = (takeN + start - 1).takeIf { it < messageCount } ?: messageCount
                val resultat =
                    inbox.getMessages(start, endIndex).map { it as MimeMessage }.toList().onEach { mimeMessage ->
                        log.info("Reading emails startIndex $start")
                        if (mimeMessage.content is MimeMultipart) {
                            runCatching {
                                (mimeMessage.content as MimeMultipart).getBodyPart(0)
                            }.onSuccess {
                                log.info(
                                    "Incoming multipart request with headers ${
                                    mimeMessage.allHeaders.toList().map { it.name + ":" + it.value }
                                    } part headers ${
                                    it.allHeaders.toList().map { it.name + ":" + it.value }
                                    }" +
                                        "with body ${String(it.inputStream.readAllBytes())}"
                                )
                            }
                        } else {
                            log.info(
                                "Incoming singlepart request with headers ${
                                mimeMessage.allHeaders.toList().map { it.name + ":" + it.value }
                                } and body ${String(mimeMessage.inputStream.readAllBytes())}"
                            )
                        }
                        val headerXMailer = mimeMessage.getHeader("X-Mailer")?.toList()?.firstOrNull()
                        log.info(
                            createHeaderMarker(headerXMailer),
                            "From: <${mimeMessage.from[0]}> Subject: <${mimeMessage.subject}>"
                        )
                        mimeMessage.setFlag(Flags.Flag.DELETED, expunge())
                    }
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

//    private fun Message.createHeaderMarker(): LogstashMarker {
//        val headerMap = mutableMapOf<String,String>()
//        this.allHeaders.iterator().forEach {
//            headerMap[it.name] = it.value
//        }
//        return Markers.appendEntries(headerMap)
//    }
}
