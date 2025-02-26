package no.nav.emottak.message.model

import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class Event(
    val eventId: Uuid,
    val requestId: Uuid,
    val contentId: String? = null,
    val messageId: String,
    val juridiskLoggId: String? = null,
    val eventMessage: String,
    val createdAt: Instant? = null
)
