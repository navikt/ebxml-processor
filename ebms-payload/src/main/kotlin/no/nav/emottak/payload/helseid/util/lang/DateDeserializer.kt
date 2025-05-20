package no.nav.emottak.payload.helseid.util.lang

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.time.ZonedDateTime
import java.util.Date

class DateDeserializer : JsonDeserializer<Date?>() {

    override fun deserialize(parser: JsonParser, context: DeserializationContext): Date? {
        return deserialize(parser.text)
    }

    companion object {
        @Suppress("ReturnCount")
        @JvmStatic fun deserialize(txt: String): Date? {
            if (txt.isBlank()) {
                return null
            }
            val txt2 = txt.trim()
            if (StringUtil.isNumeric(txt2)) {
                return Date(txt2.toLong())
            }
            val zdt: ZonedDateTime? = ZonedDateTimeDeserializer.Companion.deserialize(txt2)
            return if (zdt == null) null else Date.from(zdt.toInstant())
        }

        fun deserialize(ms: Long?): Date? {
            return if (ms != null) {
                Date(ms)
            } else null
        }

        fun deserialize(ms: Long): Date {
            return Date(ms)
        }
    }
}
