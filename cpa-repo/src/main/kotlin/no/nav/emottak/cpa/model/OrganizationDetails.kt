package no.nav.emottak.cpa.model

import kotlinx.serialization.Serializable

@Serializable
data class OrganizationDetails(
    val organizationNumber: String,
    val name: String
)
