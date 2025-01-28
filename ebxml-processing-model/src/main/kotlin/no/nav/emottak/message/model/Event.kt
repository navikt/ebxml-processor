package no.nav.emottak.message.model

import java.time.Instant
import java.util.UUID

data class Event(
    val eventId: UUID,
    val referenceId: UUID,
    val contentId: String? = null,
    val messageId: String,
    val juridiskLoggId: String? = null,
    val eventMessage: String,
    val createdAt: Instant? = null
)
