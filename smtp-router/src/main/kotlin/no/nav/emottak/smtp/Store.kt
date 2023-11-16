package no.nav.emottak.smtp

import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import no.nav.emottak.util.getEnvVar
import java.util.*


val properties = Properties().also { props ->
    props["mail.pop3.socketFactory.fallback"] = "false"
    props["mail.pop3.socketFactory.port"] = getEnvVar("SMTP_POP3_FACTORY_PORT", "3110")
    props["mail.pop3.port"] = getEnvVar("SMTP_POP3_PORT", "3110")
    props["mail.pop3.host"] = getEnvVar("SMTP_POP3_HOST", "localhost")
    props["mail.store.protocol"] = getEnvVar("SMTP_STORE_PROTOCOL", "pop3")
}

val smtpUsername = getEnvVar("SMTP_USERNAME", "test@test.test")
val smtpPassword = getEnvVar("SMTP_PASSWORD", "changeit")

val store = run {
    val auth = object : Authenticator() {
        override fun getPasswordAuthentication() = PasswordAuthentication(smtpUsername, smtpPassword)
    }
    val session = Session.getDefaultInstance(properties, auth)
    session.getStore("pop3")
}
