package no.nav.emottak.validering.sertifikat

import java.time.Duration

data class CRLConfig(
    val refreshInterval: Duration = Duration.ofHours(1)
)
