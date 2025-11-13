package no.nav.emottak.edi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = OrderBySerializer::class)
enum class OrderBy(val value: String) {
    ASC("Ascending"),
    DESC("Descending");

    companion object {
        fun fromValue(value: String?): OrderBy =
            entries.find { it.value == value } ?: ASC
    }
}

object OrderBySerializer : KSerializer<OrderBy> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("OrderBy", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: OrderBy) {
        val code = when (value) {
            OrderBy.ASC -> "1"
            OrderBy.DESC -> "2"
        }
        encoder.encodeString(code)
    }

    override fun deserialize(decoder: Decoder): OrderBy {
        return when (decoder.decodeString()) {
            "1" -> OrderBy.ASC
            "2" -> OrderBy.DESC
            else -> OrderBy.ASC
        }
    }
}
