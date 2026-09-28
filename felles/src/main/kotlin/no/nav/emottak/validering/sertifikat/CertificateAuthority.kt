package no.nav.emottak.validering.sertifikat

data class CertificateAuthority(
    val dn: String,
    val issuer: String? = null,
    val ocspUrl: String,
    val crlUrl: String? = null
)
