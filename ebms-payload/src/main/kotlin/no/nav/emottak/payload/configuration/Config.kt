package no.nav.emottak.payload.configuration

data class CertificateAuthority(
    val dn: String,
    val ocspUrl: String,
    val issuer: IssuerEnum
)

enum class IssuerEnum {
    BUYPASS, COMMFIDES
}

data class Config(
    val caList: List<CertificateAuthority>
)
