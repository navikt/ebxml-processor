package no.nav.emottak.message.model

import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class EbmsMessageDetails(
    val referenceId: Uuid,
    val cpaId: String,
    val conversationId: String,
    val messageId: String,
    val refToMessageId: String?,
    val fromPartyId: String,
    val fromRole: String?,
    val toPartyId: String,
    val toRole: String?,
    val service: String,
    val action: String,
    val sentAt: Instant? = null,
    val createdAt: Instant? = null
) {
    companion object {
        fun serializePartyId(partyIDs: List<PartyId>): String {
            val partyId = partyIDs.firstOrNull { it.type == "orgnummer" }
                ?: partyIDs.firstOrNull { it.type == "HER" }
                ?: partyIDs.firstOrNull { it.type == "ENH" }
                ?: partyIDs.first()

            return "${partyId.type}:${partyId.value}"
        }
        fun convertStringToUUIDOrGenerateNew(string: String): Uuid =
            try {
                Uuid.parse(string)
            } catch (iae: IllegalArgumentException) {
                Uuid.random() // TODO vurdere dette
            }
    }
}
