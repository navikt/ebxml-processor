package no.nav.emottak.util;

import io.ktor.server.plugins.BadRequestException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class GZipUtil {
    val GZIP_ENCODE_UTF_8 = "UTF-8"
    val GZIP_ENCODE_ISO_8859_1 = "ISO-8859-1"

    fun compress(byteArray: ByteArray): ByteArray {
        if (byteArray.isEmpty()) {
            throw BadRequestException("Empty payload")
        }
        val out = ByteArrayOutputStream()
        val gzip: GZIPOutputStream
        try {
            gzip = GZIPOutputStream(out)
            gzip.write(byteArray)
            gzip.close()
        } catch (e: Exception) {
            throw e
        }
        return out.toByteArray()
    }

    fun uncompress(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty()) {
            throw BadRequestException("Empty payload")
        }
        val out = ByteArrayOutputStream()
        val `in` = ByteArrayInputStream(bytes)
        try {
            val ungzip = GZIPInputStream(`in`)
            val buffer = ByteArray(256)
            var n: Int
            while (ungzip.read(buffer).also { n = it } >= 0) {
                out.write(buffer, 0, n)
            }
        } catch (e: Exception) {
            throw e
        }
        return out.toByteArray()
    }

    /*
     * Determines if a byte array is compressed. The java.util.zip GZip
     * implementation does not expose the GZip header so it is difficult to determine
     * if a string is compressed.
     *
     * @param bytes an array of bytes
     * @return true if the array is compressed or false otherwise
     * @throws java.io.IOException if the byte array couldn't be read
     */
    fun isCompressed(bytes: ByteArray?): Boolean {
        return if (bytes == null || bytes.size < 2) {
            false
        } else {
            bytes[0] == GZIPInputStream.GZIP_MAGIC.toByte() && bytes[1] == (GZIPInputStream.GZIP_MAGIC shr 8).toByte()
        }
    }
}
