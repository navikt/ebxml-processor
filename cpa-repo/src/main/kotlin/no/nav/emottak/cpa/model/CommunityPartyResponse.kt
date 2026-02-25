package no.nav.emottak.cpa.model

import kotlinx.serialization.Serializable

@Serializable
data class CommunityPartyResponse(
    val herId: Int,
    val name: String,
    val parentOrganizationNumber: String?,
    val organizationDetails: OrganizationDetails,
    val email: String?,
    val currentEncryptionCertificate: EncryptionCertificate?,
    val signCertificate: SignCertificate?
)
