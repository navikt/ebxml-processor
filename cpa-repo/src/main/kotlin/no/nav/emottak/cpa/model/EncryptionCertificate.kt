package no.nav.emottak.cpa.model

import kotlinx.serialization.Serializable

@Serializable
data class EncryptionCertificate(
    val thumbprint: String,
    val validFrom: String,
    val validTo: String
)
