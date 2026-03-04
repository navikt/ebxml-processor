package no.nav.emottak.cpa.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrganizationDetails(
    @SerialName("organizationNumber")
    val organizationNumber: String
)
