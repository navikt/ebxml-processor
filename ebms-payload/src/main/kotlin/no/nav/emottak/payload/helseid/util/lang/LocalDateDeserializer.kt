package no.nav.emottak.payload.helseid.util.lang

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Date

class LocalDateDeserializer : JsonDeserializer<LocalDate?>() {

    override fun deserialize(parser: JsonParser, context: DeserializationContext): LocalDate? {
        return deserialize(parser.text)
    }

    companion object {
        @JvmStatic fun deserialize(txt: String): LocalDate? {
            if (txt.isBlank()) {
                return null
            }
            val txt2 = txt.trim()
            return if (StringUtil.isNumeric(txt2)) {
                fromMillis(txt2)
            } else localize(txt2)
        }

        fun deserialize(ms: Long?): LocalDate? {
            return if (ms != null) {
                deserialize(ms.toLong())
            } else null
        }

        fun deserialize(ms: Date?): LocalDate? {
            return if (ms != null) {
                deserialize(ms.time)
            } else null
        }

        private fun fromMillis(txt: String): LocalDate {
            return deserialize(txt.toLong())
        }

        fun deserialize(ms: Long): LocalDate {
            val i = Instant.ofEpochMilli(ms)
            return LocalDate.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault().rules.getOffset(i))
        }

        private fun localize(txt: String): LocalDate? {
            val z: ZonedDateTime = ZonedDateTimeDeserializer.deserialize(txt) ?: return null
            val i = z.toInstant()
            val utcZoned = ZonedDateTime.ofInstant(i, ZoneOffset.UTC)
            return utcZoned.withZoneSameInstant(ZoneId.systemDefault().rules.getOffset(i)).toLocalDate()
        }
    }
}
