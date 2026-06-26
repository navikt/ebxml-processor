package no.nav.emottak.message.model

import kotlinx.serialization.Serializable
import no.nav.emottak.utils.common.model.PartyId

@Serializable
data class SignatureDetails(
    val certificate: ByteArray,
    val signatureAlgorithm: String,
    val hashFunction: String
)

@Serializable
data class PartyCertificates(
    val partyIds: List<PartyId>,
    val signatureDetails: SignatureDetails?,
    val encryptionCertificate: ByteArray?
)

@Serializable
data class SignatureDetailsRequest(
    val cpaId: String,
    val partyType: String,
    val partyId: String,
    val role: String,
    val service: String,
    val action: String
)
