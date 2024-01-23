package no.nav.emottak.smtp

import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart

class Router(store: Store, val outStore: Session, val transport: Transport, val expunge: Boolean = true) : AutoCloseable {

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
        inbox.close(
            expunge().also {
                if (expunge != it) {
                    log.warn("Inbox limit [$inboxLimit] exceeded. Expunge forced $it")
                }
            }
        )
    }

    fun isM18(message: MimeMultipart): Boolean {
        if (message.count == 1) return false

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
                    .forEach { msg ->
                        msg.setFlag(Flags.Flag.DELETED, expunge())
                        if (msg.content is MimeMultipart && isM18(msg.content as MimeMultipart)) {
                            //  msg.session.transport.sendMessage(msg, InternetAddress.parse(smtpUsername_outgoing_ny))
                            //   Transport.send(msg, InternetAddress.parse(smtpUsername_outgoing_gammel))
                            log.info("Routing M18 melding")
                            val melding = MimeMessage(outStore, (msg as MimeMessage).rawInputStream)
                            melding.setRecipients(Message.RecipientType.TO, smtpUsername_outgoing_ny)
                            newInboxCount++
                        } else {
                            log.info("Routing et annet type melding")
                            val melding = MimeMessage(outStore)
                            if (msg.content is Multipart) melding.setContent(msg.content as Multipart) else melding.setText(msg.content as String)
                            melding.setFrom(msg.from.iterator().next())
                            melding.subject = msg.subject
                            melding.setRecipients(Message.RecipientType.TO, smtpUsername_outgoing_ny)
                            transport.sendMessage(melding, InternetAddress.parse(smtpUsername_outgoing_ny))
                            // outStore.transport.connect("nyebmstest@test-es.nav.no","test1234")
                            // outStore.transport.sendMessage(msg, InternetAddress.parse("nyebmstest@test-es.nav.no"))

                         /*  val trans = msg.session.transport
                                trans.connect("nyebmstest@test-es.nav.no","test1234")
                                trans.sendMessage(melding, InternetAddress.parse("test@test.test"))
                            // */
                            oldInboxCount++
                        }
                    }
                start += takeN
            }
            return Pair(oldInboxCount, newInboxCount)
        } catch (e: Exception) {
            log.error("Error connecting to mail server", e)
            throw e
        }
    }
}
