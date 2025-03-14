package no.nav.emottak.utils.events.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.emottak.utils.UuidSerializer
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Event(
    val eventType: EventType,
    @Serializable(with = UuidSerializer::class)
    val requestId: Uuid,
    val contentId: String? = null,
    val messageId: String,
    val eventData: String? = null
) {
    fun toByteArray(): ByteArray {
        return Json.encodeToString(this).toByteArray()
    }
}
