package no.nav.emottak.payload.ocspstatus

data class SertifikatInfo(
    val serienummer: String,
    val status: SertifikatStatus,
    val type: SertifikatType,
    val seid: SEIDVersion,
    val gyldigFra: String,
    val gyldigTil: String,
    val utsteder: String,
    val orgnummer: String? = null,
    val fnr: String? = null,
    val beskrivelse: String,
    val feilmelding: String? = null
)

enum class SertifikatStatus {
    OK, REVOKERT, UKJENT
}

enum class SertifikatType {
    PERSONLIG, VIRKSOMHET
}

enum class SEIDVersion {
    SEID10, SEID20
}
