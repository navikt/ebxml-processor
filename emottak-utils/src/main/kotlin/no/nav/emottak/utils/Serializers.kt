package no.nav.emottak.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Inspirert av: https://stackoverflow.com/questions/65398284/kotlin-serialization-serializer-has-not-been-found-for-type-uuid
@OptIn(ExperimentalUuidApi::class)
object UuidSerializer : KSerializer<Uuid> {
    override val descriptor = PrimitiveSerialDescriptor("Uuid", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder) = Uuid.parse(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: Uuid) = encoder.encodeString(value.toString())
}

// Hentet fra: https://stackoverflow.com/questions/71629874/how-to-use-kotlinx-serialization-serializable-with-java-time-instant
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

fun Exception.toJsonString(): String {
    return "{\"ExceptionClass\":\"${this.javaClass.name}\", \"message\":\"${this.message}\", \"causeMessage\":\"${this.cause?.message}\"}"
}
