package no.nav.emottak.payload.configuration

data class CertificateAuthority(
    val dn: String,
    val ocspUrl: String
)

data class Config(
    val caList: List<CertificateAuthority>
)
