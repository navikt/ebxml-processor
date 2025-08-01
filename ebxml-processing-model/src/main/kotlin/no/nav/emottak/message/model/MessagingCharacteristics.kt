package no.nav.emottak.message.model

import kotlinx.serialization.Serializable
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.PerMessageCharacteristicsType

@Serializable
data class MessagingCharacteristicsRequest(
    val requestId: String,
    val cpaId: String,
    val partyIds: List<PartyId>,
    val role: String,
    val service: String,
    val action: String
)

@Serializable
data class MessagingCharacteristicsResponse(
    val requestId: String,
    val ackRequested: PerMessageCharacteristicsType?,
    val ackSignatureRequested: PerMessageCharacteristicsType?,
    val duplicateElimination: PerMessageCharacteristicsType?
)
