package no.nav.emottak.edi

import kotlinx.serialization.Serializable

@Serializable
data class GetMessagesRequest(
    val receiverHerIds: List<Int>,
    val senderHerId: Int? = null,
    val businessDocumentId: String? = null,
    val includeMetadata: Boolean = false,
    val messagesToFetch: Int = 10,
    val orderBy: OrderBy = OrderBy.ASC
) {
    fun toUrlParams(): String {
        val params = mutableListOf<String>()

        if (receiverHerIds.isNotEmpty()) {
            receiverHerIds.forEach {
                params += "receiverHerIds=$it"
            }
        }

        senderHerId?.let {
            params += "senderHerId=$it"
        }

        businessDocumentId?.let {
            params += "businessDocumentId=$it"
        }

        params += "includeMetadata=$includeMetadata"
        params += "messagesToFetch=$messagesToFetch"
        params += "orderBy=${orderBy.name}"

        return params.joinToString("&")
    }
}
