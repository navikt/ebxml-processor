package no.nav.emottak.message.model

import java.time.Instant
import java.util.UUID

data class Event(
    val eventId: UUID,
    val referenceId: UUID,
    val contentId: String?,
    val messageId: String,
    val juridiskLoggId: String?,
    val eventMessage: String,
    val createdAt: Instant?
)
