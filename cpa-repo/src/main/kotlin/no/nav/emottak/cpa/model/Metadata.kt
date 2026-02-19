package no.nav.emottak.cpa.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class Metadata(
    val id: Uuid,
    val location: String
)
