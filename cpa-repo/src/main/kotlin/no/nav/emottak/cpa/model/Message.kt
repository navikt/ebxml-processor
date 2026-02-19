package no.nav.emottak.cpa.model
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Message constructor(
    val id: Uuid? = null,
    val contentType: String? = null,
    val receiverHerId: Int? = null,
    val senderHerId: Int? = null,
    val businessDocumentId: String? = null,
    @Contextual val businessDocumentGenDate: Instant? = null,
    val isAppRec: Boolean? = null,
    val sourceSystem: String? = null
)
