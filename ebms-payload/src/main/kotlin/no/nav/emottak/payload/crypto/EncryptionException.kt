package no.nav.emottak.payload.crypto

class EncryptionException(override val message: String, e: Exception? = null) : Exception(message, e)
