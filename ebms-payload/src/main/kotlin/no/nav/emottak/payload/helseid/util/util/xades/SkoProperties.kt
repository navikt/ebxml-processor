package no.nav.emottak.payload.helseid.util.util.xades


data class SkoProperties(
    val certificateListPath: String = "",
    val keystorePath: String = "",
    val keystorePassword: String = "",
    val keystoreType: String = "",
    val personalPolicyIds: Collection<String> = emptyList(),
    val enterprisePolicyIds: Collection<String> = emptyList(),
    val helseIdIssuer: String = XAdESVerifier.HELSE_ID_ISSUER,
    val issuerOrganizationNumber: String = XAdESVerifier.HELSE_ID_ORGNO,
    val helseIdAllowedClockSkewInMs: Long = 0
)
