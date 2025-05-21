package no.nav.emottak.payload.helseid.testutils

import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Base64

/**
 * Methods for reading resources.
 */
@Suppress("TooManyFunctions")
object ResourceUtil {
    /**
     * Gets a byte array from a resource that's in the classpath or on an absolute path.
     * @param path The path.
     * @param b64decode if true and the path ends in .b64 we return the decoded contents
     * @return A byte array.
     * @throws java.io.IOException if not found.
     */
    fun getByteArrayClasspathOrAbsolutePathResource(path: String, b64decode: Boolean = false): ByteArray {
        val p = Paths.get(path)
        return when {
            !p.isAbsolute -> getByteArrayClasspathResource(path, b64decode)
            b64decode && path.endsWith(".b64") -> Base64.getDecoder().decode(Files.readAllBytes(p))
            else -> Files.readAllBytes(p)
        }
    }

    /**
     * Gets a byte array from a resource that's in the classpath.
     * @param path The path.
     * @param b64decode if true and the path ends in .b64 we return the decoded contents
     * @return A byte array.
     * @throws java.io.IOException if not found.
     */
    fun getByteArrayClasspathResource(path: String, b64decode: Boolean = false): ByteArray {
        val r = InternalResource(path)
        return if (b64decode && path.endsWith(".b64")) {
            Base64.getDecoder().decode(r.inputStream.use { it.readBytes() })
        } else {
            r.inputStream.use { it.readBytes() }
        }
    }

    /**
     * Gets a string with UTF-8 encoding from a resource that's in the classpath.
     * @param path The path.
     * @return A string.
     * @throws java.io.IOException if not found.
     */
    fun getStringClasspathResource(path: String): String {
        val r = InternalResource(path)
        return r.inputStream.use { s -> s.bufferedReader().use { b -> b.readText() } }
    }

    class InternalResource(private val path: String) {

        val url: URL? = this::class.java.classLoader.getResource(path)
        val exists = url != null
        val inputStream: InputStream by lazy {
            urlNotNull.openStream()
        }

        private val urlNotNull by lazy {
            if (exists) {
                url!!
            } else {
                throw FileNotFoundException(
                    "class path resource [$path] cannot be resolved to URL because it does not exist"
                )
            }
        }
    }
}
