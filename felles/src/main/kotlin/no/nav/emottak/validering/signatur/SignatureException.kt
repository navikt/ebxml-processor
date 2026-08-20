package no.nav.emottak.validering.signatur

class SignatureException(override val message: String, e: Exception? = null) : Exception(message, e)
