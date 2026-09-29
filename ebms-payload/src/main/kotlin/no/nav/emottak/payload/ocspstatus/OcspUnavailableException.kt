package no.nav.emottak.payload.ocspstatus

class OcspUnavailableException(override val message: String?, cause: Throwable?) : Exception(message, cause)
