package no.nav.emottak.message.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Base64

class PayloadSerializationTest {
    private val payload = Payload(
        bytes = ByteArray(256) { it.toByte() },
        contentType = "application/xml",
        contentId = "attachment-test",
        signedByPid = "test-pid",
        signedByOrg = "test-org"
    )

    @Test
    fun `request payload bytes are a Base64 string and round trip without metadata loss`() {
        val party = Party(listOf(PartyId("HERID", "test")), "test")
        val request = PayloadRequest(
            direction = Direction.IN,
            messageId = "message",
            conversationId = "conversation",
            processing = PayloadProcessing(
                signingCertificate = SignatureDetails(byteArrayOf(), "algorithm", "hash"),
                encryptionCertificate = byteArrayOf(),
                processConfig = ProcessConfig(
                    kryptering = false,
                    komprimering = false,
                    signering = false,
                    internformat = false,
                    validering = false,
                    apprec = false,
                    ocspSjekk = false,
                    juridiskLogg = false,
                    adapter = null,
                    errorAction = null
                )
            ),
            payload = payload,
            addressing = Addressing(to = party, from = party, service = "service", action = "action"),
            requestId = "request"
        )

        val json = Json.encodeToString(request)
        val encodedPayload = Json.parseToJsonElement(json).jsonObject.getValue("payload").jsonObject
        assertEquals(Base64.getEncoder().encodeToString(payload.bytes), encodedPayload.getValue("bytes").jsonPrimitive.content)
        assertEquals(true, encodedPayload.getValue("bytes").jsonPrimitive.isString)

        val decoded = Json.decodeFromString<PayloadRequest>(json)
        assertPayloadEquals(payload, decoded.payload)
        assertEquals(request.messageId, decoded.messageId)
        assertEquals(request.conversationId, decoded.conversationId)
        assertEquals(request.requestId, decoded.requestId)
        assertEquals(request.direction, decoded.direction)
        assertEquals(request.addressing, decoded.addressing)
    }

    @Test
    fun `response payload bytes use Base64 including empty and padding boundaries`() {
        for (size in listOf(0, 1, 2, 3, 256, 1024 * 1024)) {
            val original = payload.copy(bytes = ByteArray(size) { it.toByte() })
            val response = PayloadResponse(processedPayload = original, juridiskLoggRecordId = "record")
            val json = Json.encodeToString(response)
            val encodedPayload = Json.parseToJsonElement(json).jsonObject.getValue("processedPayload").jsonObject
            val encodedBytes = encodedPayload.getValue("bytes").jsonPrimitive

            assertEquals(true, encodedBytes.isString)
            assertEquals(Base64.getEncoder().encodeToString(original.bytes), encodedBytes.content)
            assertEquals(4 * ((size + 2) / 3), encodedBytes.content.length)

            val decoded = Json.decodeFromString<PayloadResponse>(json)
            assertPayloadEquals(original, decoded.processedPayload!!)
            assertEquals("record", decoded.juridiskLoggRecordId)
        }
    }

    @Test
    fun `invalid Base64 and legacy numeric arrays are rejected`() {
        for (bytes in listOf("\"not!base64\"", "[0,1,2]", "null")) {
            assertThrows(SerializationException::class.java) {
                Json.decodeFromString<Payload>(
                    """{"bytes":$bytes,"contentType":"application/xml","contentId":"test"}"""
                )
            }
        }
    }

    private fun assertPayloadEquals(expected: Payload, actual: Payload) {
        assertArrayEquals(expected.bytes, actual.bytes)
        assertEquals(expected.copy(bytes = actual.bytes), actual)
    }
}
