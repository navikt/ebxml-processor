package no.nav.emottak.payload.helseid.util.lang

import java.awt.event.KeyEvent
import java.lang.Character.UnicodeBlock
import java.util.Base64
import java.util.Locale

@Suppress("TooManyFunctions")
object ByteUtil {
    private val toBase64 = charArrayOf(
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
        'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
    )

    @Suppress("MagicNumber")
    private val fromBase64 = IntArray(256)
    private val decoder = Base64.getDecoder()

    // squid:S4784 Using regular expressions is security-sensitive
    private val B64_PATTERN = Regex("^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?$")
    private val CR_TAB_NL_PATTERN = Regex("[\\r\\t\\n]")
    private val NL_CR_PATTERN = Regex("[\n\r]")
    private const val B64_MULTIPLE = 4

    /**
     * returns a formatted hex dump of a byte array
     * example output:
     *
     * 00000000:  31 32 33 34 35 36 37 38 31 32 33 34 35 36 37 38   |  1234567812345678
     * 00000016:  31 32 33 34 35 36 37 38 38 37 36 35 34 33 32 31   |  1234567887654321
     * 00000032:  C3 A6 C3 B8 C3 A5 C3 BC                           |  æøåü
     *
     * unprintable bytes, including \n etc, are shown as (char) 0.
     * @param array the array of bytes
     * @return formatted hex dump
     */
    fun formatHexDump(array: ByteArray): String {
        return formatHexDump(array, 0, array.size, '.')
    }

    /**
     * returns a formatted hex dump of a byte array
     * example output:
     *
     * 00000000:  31 32 33 34 35 36 37 38 31 32 33 34 35 36 37 38   |  1234567812345678
     * 00000016:  31 32 33 34 35 36 37 38 38 37 36 35 34 33 32 31   |  1234567887654321
     * 00000032:  C3 A6 C3 B8 C3 A5 C3 BC                           |  æøåü
     *
     * unprintable bytes, including \n etc, are shown as (char) 0.
     * @param array the array of bytes
     * @param offset the offset
     * @param length the length
     * @param unprintable replace unprintable characters with this
     * @return formatted hex dump
     */
    private fun formatHexDump(array: ByteArray, offset: Int, length: Int, unprintable: Char): String {
        @Suppress("MagicNumber")
        val width = 16
        val builder = StringBuilder()
        var rowOffset = offset
        while (rowOffset < offset + length) {
            builder.append(String.format(Locale.getDefault(), "%08d:  ", rowOffset))
            for (index in 0 until width) {
                if (rowOffset + index < array.size) {
                    builder.append(String.format(Locale.getDefault(), "%02X ", array[rowOffset + index]))
                } else {
                    builder.append("   ")
                }
            }
            if (rowOffset < array.size) {
                val asciiWidth = width.coerceAtMost(array.size - rowOffset)
                builder.append("  |  ")
                appendPrintablePart(builder, array, rowOffset, asciiWidth, unprintable)
            }
            builder.append(String.format(Locale.getDefault(), "%n"))
            rowOffset += width
        }
        return builder.toString()
    }

    private fun appendPrintablePart(
        builder: StringBuilder,
        array: ByteArray,
        rowOffset: Int,
        asciiWidth: Int,
        unprintable: Char
    ) {
        val unprintableStr = String(charArrayOf(unprintable))
        val tmp = String(array, rowOffset, asciiWidth, Charsets.UTF_8)
            .replace(CR_TAB_NL_PATTERN, unprintableStr)
        for (c in tmp) {
            builder.append(if (!isPrintable(c)) unprintable else c)
        }
    }

    /**
     * Checks if a character is printable.
     * @param c The character.
     * @return true if printable, false otherwise.
     */
    fun isPrintable(c: Char): Boolean {
        val block = UnicodeBlock.of(c)
        return !Character.isISOControl(c) && block != null &&
            block !== UnicodeBlock.SPECIALS && c != KeyEvent.CHAR_UNDEFINED
    }

    /**
     * Decodes a base64 byte array. Supports chunked encoding.
     * @param src The source.
     * @return The decoded byte array.
     */
    fun decodeBase64(src: String): ByteArray {
        return decodeBase64(src.toByteArray())
    }

    /**
     * Decodes a base64 byte array. Supports chunked encoding.
     * @param src The source.
     * @return The decoded byte array.
     */
    fun decodeBase64(src: ByteArray): ByteArray {
        val dst = ByteArray(src.size)
        var j = 0
        for (b in src) {
            if (fromBase64[b.toInt()] > 0) {
                dst[j++] = b
            }
        }
        return decoder.decode(dst.copyOf(j))
    }

    /**
     * Checks if the input could be base64 encoded.
     * @param input The input.
     * @return True if the input could be base64 encoded.
     */
    fun isBase64Encoded(input: ByteArray?): Boolean {
        return if (input != null) {
            isBase64Encoded(String(input))
        } else {
            false
        }
    }

    /**
     * Checks if the input could be base64 encoded.
     * @param input The input.
     * @return True if the input could be base64 encoded.
     */
    fun isBase64Encoded(input: String?): Boolean =
        if (!input.isNullOrEmpty()) {
            B64_PATTERN.matches(input.replace(NL_CR_PATTERN, ""))
        } else {
            false
        }

    /**
     * Returns a text with padding so that the length is a multiple of 4
     * @param text text to pad
     * @return padded text
     */
    fun base64Pad(text: String): String {
        val modLen = text.length % B64_MULTIPLE
        return if (modLen == 0) {
            text
        } else {
            val padLen = (B64_MULTIPLE - modLen) + text.length
            text.padEnd(padLen, '=')
        }
    }

    init {
        fromBase64.fill(-1)
        for (c in toBase64) {
            fromBase64[c.code] = 1
        }
        fromBase64['='.code] = 1
    }
}
