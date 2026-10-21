package no.nav.emottak.message.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.util.Base64

object Base64ByteArraySerializer : KSerializer<ByteArray> {
    override val descriptor = PrimitiveSerialDescriptor("Base64ByteArray", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(Base64.getEncoder().encodeToString(value))
    }

    override fun deserialize(decoder: Decoder): ByteArray {
        if (decoder !is JsonDecoder) return decodeBase64(decoder.decodeString())
        return when (val element = decoder.decodeJsonElement()) {
            // Legacy numeric array format, still accepted from pods running an older version
            is JsonArray -> decodeLegacyArray(element)
            is JsonPrimitive -> if (element.isString) {
                decodeBase64(element.content)
            } else {
                throw SerializationException("Payload bytes must be a Base64 string")
            }
            else -> throw SerializationException("Payload bytes must be a Base64 string")
        }
    }

    private fun decodeLegacyArray(array: JsonArray): ByteArray = ByteArray(array.size) { i ->
        val value = (array[i] as? JsonPrimitive)
            ?.takeUnless { it.isString }
            ?.intOrNull
            ?.takeIf { it in Byte.MIN_VALUE..Byte.MAX_VALUE }
            ?: throw SerializationException("Invalid legacy payload byte at index $i")
        value.toByte()
    }

    private fun decodeBase64(encoded: String): ByteArray {
        return try {
            Base64.getDecoder().decode(encoded)
        } catch (e: IllegalArgumentException) {
            throw SerializationException("Invalid Base64 payload bytes", e)
        }
    }
}
