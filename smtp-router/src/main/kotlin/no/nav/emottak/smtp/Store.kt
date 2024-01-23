package no.nav.emottak.smtp

import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Store
import java.util.*

val properties = Properties().also { props ->
    props["mail.pop3.socketFactory.fallback"] = "false"
    props["mail.pop3.socketFactory.port"] = getEnvVar("SMTP_POP3_FACTORY_PORT", "3110")
    props["mail.pop3.port"] = getEnvVar("SMTP_POP3_PORT", "3110")
    props["mail.pop3.host"] = getEnvVar("SMTP_POP3_HOST", "localhost")
    props["mail.smtp.port"] = getEnvVar("SMTP_SMTP_PORT", "3125")
    props["mail.smtp.host"] = getEnvVar("SMTP_POP3_HOST", "localhost")
    props["mail.smtp.socketFactory.fallback"] = "false"
    props["mail.imap.socketFactory.port"] = getEnvVar("SMTP_SMTP_PORT", defaultValue = "3125")
    props["mail.imap.socketFactory.port"] = "143"
    props["mail.imap.port"] = "143"
    props["mail.imap.host"] = "d32mxvl002.oera-t.local"
}

val smtpUsername_incoming = getEnvVar("SMTP_BCC_USERNAME", "test@test.test") // getEnvVar("SMTP_INCOMING_USERNAME", "test@test.test")
val smtpUsername_bcc = getEnvVar("SMTP_BCC_USERNAME", "test@test.test")
val smtpUsername_outgoing_gammel = getEnvVar("SMTP_OUTGOING_USERNAME_GAMMEL", "test@test.test")
val smtpUsername_outgoing_ny = getEnvVar("SMTP_OUTGOING_USERNAME", "test@test.test")
val smtpPassword = getEnvVar("SMTP_PASSWORD", "changeit")

var session = run {
    createSession(smtpUsername_incoming, smtpPassword)
}

val incomingStore = run {
    session.getStore("pop3").apply {
        connect()
    }
}

private fun createSession(username: String, password: String): Session {
    val auth = object : Authenticator() {
        override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
    }
    return Session.getInstance(properties, auth)
}

private fun createStore(username: String, password: String, protokol: String = "pop3"): Store {
    return session.getStore(protokol).also {
        it.connect()
    }
}
