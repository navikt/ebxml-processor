package no.nav.emottak.utils.events.model

import kotlinx.serialization.Serializable
import no.nav.emottak.utils.UuidSerializer
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Event(
    // @Serializable(with = UuidSerializer::class)
    // val eventId: Uuid,                   // Genereres ved insert til DB
    val eventType: EventType,
    @Serializable(with = UuidSerializer::class)
    val requestId: Uuid,
    val contentId: String? = null,
    val messageId: String,
    val eventData: String? = null // TODO: ByteArray? Eller JSON? Eller String?
    // val createdAt: Instant? = null       // Genereres ved insert til DB
)
