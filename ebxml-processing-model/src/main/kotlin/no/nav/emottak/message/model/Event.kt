package no.nav.emottak.message.model

import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class Event(
    val eventId: Uuid,
    val referenceId: Uuid,
    val contentId: String?,
    val messageId: String,
    val juridiskLoggId: String?,
    val eventMessage: String,
    val createdAt: Instant?
)
