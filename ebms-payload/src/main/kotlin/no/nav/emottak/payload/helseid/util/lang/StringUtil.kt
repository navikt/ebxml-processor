package no.nav.emottak.payload.helseid.util.lang

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper

object StringUtil {

    /**
     * abbreviates a string taking into account the length of the string as byte array
     * @param txt the text to abbreviate, can be null
     * @param max the max length of the string after potential abbreviation including the abbrevMarker
     * @param abbrevMarker optional marker to put at end of string to indicate the the string has been abbreviated
     * @return the abbreviated string
     */
    fun abbreviate(txt: String?, max: Int, abbrevMarker: String? = null): String? = if (txt == null) {
        null
    } else {
        val bin = txt.toByteArray()
        val len = bin.size
        if (len <= max) {
            txt
        } else {
            if (abbrevMarker == null) {
                String(bin.copyOfRange(0, max))
            } else {
                String(bin.copyOfRange(0, max - abbrevMarker.toByteArray().size)) + abbrevMarker
            }
        }
    }

    /**
     * converts a separated string to a collection of longs
     * @param str the string
     * @param delim the delimiter, default comma (,)
     * @return collection of longs
     */
    fun splitToLongCollection(str: String, delim: Char = ','): Collection<Long> =
        str.filterNot { it.isWhitespace() }
            .split(delim)
            .filterNot { it.isEmpty() }
            .map { it.toLong() }

    /**
     * converts a separated string to a collection of strings with no spaces and no empty strings
     * @param str the string
     * @param delim the delimiter, default comma (,)
     * @return collection of strings
     */
    fun splitToStringCollection(str: String, delim: Char = ','): Collection<String> =
        str.filterNot { it.isWhitespace() }
            .split(delim)
            .filterNot { it.isEmpty() }

    /**
     * checks if string is valid json
     * @param str the string to test
     * @return true if valid json
     */
    @Suppress("SwallowedException", "EmptyWhileBlock")
    fun isValidJson(str: String): Boolean =
        try {
            val parser: JsonParser = ObjectMapper().createParser(str)
            while (parser.nextToken() != null) {

            }
            true
        } catch (e: JsonParseException) {
            false
        }

    /**
     * checks if all characters in the string is numeric
     * @param str the string to check
     * @return true if the string contains only digits, false otherwise
     */
    fun isNumeric(str: String?) = !str.isNullOrEmpty() && str.all { it.isDigit() }
}
