package no.nav.emottak.payload.helseid.util.lang

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Date

class LocalDateTimeDeserializer : JsonDeserializer<LocalDateTime?>() {

    override fun deserialize(parser: JsonParser, context: DeserializationContext): LocalDateTime? {
        return deserialize(parser.text)
    }

    companion object {
        @JvmStatic fun deserialize(txt: String?): LocalDateTime? {
            if (txt.isNullOrBlank()) {
                return null
            }
            val txt2 = txt.trim()
            return if (StringUtil.isNumeric(txt2)) {
                fromMillis(txt2)
            } else localize(txt)
        }

        fun deserialize(ms: Long?): LocalDateTime? {
            return if (ms != null) {
                deserialize(ms.toLong())
            } else null
        }

        fun deserialize(ms: Date?): LocalDateTime? {
            return if (ms != null) {
                deserialize(ms.time)
            } else null
        }

        private fun fromMillis(txt: String): LocalDateTime {
            return deserialize(txt.toLong())
        }

        fun deserialize(ms: Long): LocalDateTime {
            val i = Instant.ofEpochMilli(ms)
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault().rules.getOffset(i))
        }

        private fun localize(txt: String): LocalDateTime? {
            val z: ZonedDateTime = ZonedDateTimeDeserializer.deserialize(txt) ?: return null
            val i = z.toInstant()
            val utcZoned = ZonedDateTime.ofInstant(i, ZoneOffset.UTC)
            return utcZoned.withZoneSameInstant(ZoneId.systemDefault().rules.getOffset(i)).toLocalDateTime()
        }
    }
}
