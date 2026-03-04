package no.nav.emottak.cpa.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CurrentSigningCertificate(
    @SerialName("thumbprint")
    val thumbprint: String,

    @SerialName("validFrom")
    val validFrom: String,

    @SerialName("validTo")
    val validTo: String
)
