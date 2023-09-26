package no.nav.emottak.util.crypto

class EncryptionException(override val message: String, e: Exception? = null) : Exception(message, e) {
}