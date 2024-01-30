package no.nav.emottak.smtp
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage

class MailWriter(private val session: Session) {

    fun sendMailTo(body: String, recipient: String) {
        try {
            var message: Message = MimeMessage(session)
            message.setFrom(InternetAddress(smtpUsername_outgoing))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient))
            message.setSubject("Apprec") // TODO
            message.setText(body)
            Transport.send(message)
            log.info("Email Message Sent Successfully")
        } catch (e: MessagingException) {
            throw RuntimeException(e)
        }
    }
}
