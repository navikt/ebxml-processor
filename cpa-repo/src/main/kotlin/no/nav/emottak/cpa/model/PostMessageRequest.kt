package no.nav.emottak.cpa.model

import kotlinx.serialization.Serializable
import no.nav.helsemelding.ediadapter.model.EbXmlInfo

@Serializable
data class PostMessageRequest(
    val businessDocument: String,
    val contentType: String,
    val contentTransferEncoding: String,
    val ebXmlOverrides: EbXmlInfo? = null,
    val receiverHerIdsSubset: List<Int>? = null
)
