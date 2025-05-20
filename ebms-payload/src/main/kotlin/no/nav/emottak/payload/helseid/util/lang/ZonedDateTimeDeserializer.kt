package no.nav.emottak.payload.helseid.util.lang

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

/**
 * Deserialize zoned date time. If zone is missing local timezone is assumed.
 */
class ZonedDateTimeDeserializer : JsonDeserializer<ZonedDateTime?>() {

    override fun deserialize(parser: JsonParser, context: DeserializationContext): ZonedDateTime? {
        return deserialize(parser.text)
    }

    companion object {
        @Suppress("MaxLineLength")
        private val FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[[.SSSSSSSSS][.SSSSSSSS][.SSSSSSS][.SSSSSS][.SSSSS][.SSSS][.SSS][.SS][.S]][XXXXX][XXXX]")

        @Suppress("MagicNumber", "ReturnCount", "SwallowedException")
        @JvmStatic fun deserialize(txt: String): ZonedDateTime? {
            if (txt.isBlank()) {
                return null
            }
            var txt2 = txt.trim()
            if (StringUtil.isNumeric(txt2)) {
                return fromMillis(txt2)
            }
            when (txt.length) {
                10 -> return addTimeZone(txt + "T00:00:00")
                19 ->  return addTimeZone(txt)
                11, 15, 16 -> txt2 = txt2.substring(0, 10) + "T00:00:00" + txt2.substring(10)
            }
            return try {
                ZonedDateTime.parse(txt2, FORMATTER)
            } catch (e: DateTimeException) {
                addTimeZone(txt2)
            }
        }

        fun deserialize(ms: Long?): ZonedDateTime? {
            return if (ms != null) {
                deserialize(ms.toLong())
            } else null
        }

        fun deserialize(ms: Date?): ZonedDateTime? {
            return if (ms != null) {
                deserialize(ms.time)
            } else null
        }

        private fun addTimeZone(txt: String): ZonedDateTime {
            val t = LocalDateTime.parse(txt, FORMATTER)
            val z = ZoneId.systemDefault().rules.getOffset(t).toString()
            return ZonedDateTime.parse(txt + z, FORMATTER)
        }

        private fun fromMillis(txt: String): ZonedDateTime {
            return deserialize(txt.toLong())
        }

        fun deserialize(ms: Long): ZonedDateTime {
            val i = Instant.ofEpochMilli(ms)
            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault().rules.getOffset(i))
        }
    }
}
