package no.nav.emottak.validering.sertifikat

import java.time.Duration

data class CRLConfig(
    val refreshInterval: Duration = Duration.ofHours(1)
)

class CRLException(message: String, cause: Throwable? = null) : Exception(message, cause)
