package no.nav.emottak.utils.events.model

import kotlinx.serialization.Serializable
import no.nav.emottak.utils.InstantSerializer
import no.nav.emottak.utils.UuidSerializer
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class EventMessageDetails(
    @Serializable(with = UuidSerializer::class)
    val requestId: Uuid,
    val cpaId: String,
    val conversationId: String,
    val messageId: String,
    val refToMessageId: String? = null,
    val fromPartyId: String,
    val fromRole: String? = null,
    val toPartyId: String,
    val toRole: String? = null,
    val service: String,
    val action: String,
    @Serializable(with = InstantSerializer::class)
    val sentAt: Instant? = null
)
