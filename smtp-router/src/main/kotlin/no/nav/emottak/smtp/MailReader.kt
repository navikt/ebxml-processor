package no.nav.emottak.smtp;


import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Store
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.internet.MimeUtility
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import no.nav.emottak.util.getEnvVar

class MailReader(store: Store, val expunge: Boolean = true) : AutoCloseable {

    val inbox: Folder = store.getFolder("INBOX")

    init {
        inbox.open(Folder.READ_WRITE)
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

    fun isM18(message: MimeMultipart): Boolean {
        if(message.count == 1) return false

        val bodyPart = message.getBodyPart(0)
        return (bodyPart.content as String)
            .let { it.contains("Oppgjørskrav") && it.contains("BehandlerKrav") }
    }

    @Throws(Exception::class)
    fun routeMail(): Pair<Int, Int> {
        try {
            var oldInboxCount = 0
            var newInboxCount = 0
            val messageCount = inbox.messageCount
            if (messageCount != 0) {
                val endIndex = (takeN + start - 1).takeIf { it < messageCount } ?: messageCount
                inbox.getMessages(start, endIndex)
                    .forEach {
                        it.setFlag(Flags.Flag.DELETED, expunge())
                        if (it.content is MimeMultipart && isM18(it.content as MimeMultipart)) {
                            it.session.transport.sendMessage(it, InternetAddress.parse(smtpUsername_outgoing_ny))
                            newInboxCount++
                        } else {
                            it.session.transport.sendMessage(it, InternetAddress.parse(smtpUsername_outgoing_gammel))
                            oldInboxCount++
                        }
                    }
                start += takeN
            }
            return Pair(oldInboxCount,newInboxCount)
        } catch (e: Exception) {
            log.error("Error connecting to mail server", e)
            throw e
        }
    }
}