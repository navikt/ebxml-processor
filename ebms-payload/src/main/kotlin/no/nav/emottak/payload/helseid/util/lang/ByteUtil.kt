package no.nav.emottak.payload.helseid.util.lang

import java.util.Base64

@Suppress("TooManyFunctions")
object ByteUtil {
    private val toBase64 = charArrayOf(
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
        'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
    )

    private val fromBase64 = IntArray(256)
    private val decoder = Base64.getDecoder()

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

    init {
        fromBase64.fill(-1)
        for (c in toBase64) {
            fromBase64[c.code] = 1
        }
        fromBase64['='.code] = 1
    }
}
