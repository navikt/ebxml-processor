package no.nav.emottak.smtp

import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Store
import no.nav.emottak.util.getEnvVar
import java.util.*


val properties = Properties().also { props ->
    props["mail.pop3.socketFactory.fallback"] = "false"
    props["mail.pop3.socketFactory.port"] = getEnvVar("SMTP_POP3_FACTORY_PORT", "3110")
    props["mail.pop3.port"] = getEnvVar("SMTP_POP3_PORT", "3110")
    props["mail.pop3.host"] = getEnvVar("SMTP_POP3_HOST", "localhost")
    props["mail.store.protocol"] = getEnvVar("SMTP_STORE_PROTOCOL", "pop3")
}

val smtpUsername_incoming = getEnvVar("SMTP_INCOMING_USERNAME", "test@test.test")
val smtpUsername_bcc = getEnvVar("SMTP_BCC_USERNAME", "test@test.test")
val smtpUsername_outgoing = getEnvVar("SMTP_OUTGOING_USERNAME", "test@test.test")
val smtpPassword = getEnvVar("SMTP_PASSWORD", "changeit")

val incomingStore = run {
     createStore(smtpUsername_incoming, smtpPassword)
}

val bccStore = run {
    createStore(smtpUsername_bcc, smtpPassword)
}

val outgoingStore = run {
    createStore(smtpUsername_outgoing, smtpPassword)
}

val imapStore = createStore(smtpUsername_outgoing, smtpPassword,"imap")


private fun createStore(username:String,password:String, protokol:String = "pop3") : Store {
     val auth = object : Authenticator() {
        override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
    }
    val session = Session.getDefaultInstance(properties, auth)
    return session.getStore(protokol).also {
        it.connect()
    }
}
