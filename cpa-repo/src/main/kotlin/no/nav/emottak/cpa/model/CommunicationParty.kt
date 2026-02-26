package no.nav.emottak.cpa.model

data class CommunicationParty(
    val name: String,
    val parentOrganizationNumber: String,
    val email: String,
    val currentSigningCertificat: CurrentSigningCertificat,
    val currentEncryptionCertificate: CurrentEncryptionCertificate
)
