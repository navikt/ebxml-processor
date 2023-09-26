package no.nav.emottak.util.signatur

class SignatureException(override val message: String, e: Exception? = null) : Exception(message, e) {
}