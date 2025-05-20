package no.nav.emottak.payload.helseid.util.util

import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
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
     * @throws IOException if not found.
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
     * Gets a string from a resource that's in the classpath or on an absolute path.
     * @param path The path.
     * @return A byte array.
     * @throws IOException if not found.
     */
    fun getStringClasspathOrAbsolutePathResource(path: String): String {
        val p = Paths.get(path)
        return when {
            !p.isAbsolute -> getStringClasspathResource(path)
            else -> Files.readString(p)
        }
    }

    /**
     * Gets a byte array from a resource that's in the classpath.
     * @param path The path.
     * @param b64decode if true and the path ends in .b64 we return the decoded contents
     * @return A byte array.
     * @throws IOException if not found.
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
     * @throws IOException if not found.
     */
    fun getStringClasspathResource(path: String): String {
        val r = InternalResource(path)
        return r.inputStream.use { s -> s.bufferedReader().use { b -> b.readText() } }
    }

    /**
     * Gets a string from a resource that's in the classpath.
     * @param path The path.
     * @param charset The encoding.
     * @return A string.
     * @throws IOException if not found.
     */
    fun getStringClasspathResource(path: String, charset: Charset?): String {
        val r = InternalResource(path)
        return r.inputStream.use {
            String(it.readAllBytes(), charset ?: Charsets.UTF_8)
        }
    }

    /**
     * Gets a Path from a path found in the classpath.
     * @param path the pathname in the classpath.
     * @return the Path.
     * @throws IOException if file not found.
     */
    fun getPathClasspathResource(path: String): Path {
        return getFileClasspathResource(path).toPath()
    }

    /**
     * Gets a File from a path found in the classpath.
     * @param path the pathname in the classpath.
     * @return the File.
     * @throws IOException if file not found.
     */
    @JvmStatic fun getFileClasspathResource(path: String): File {
        val r = InternalResource(path)
        return r.file
    }

    /**
     * Gets a File from a path found in the classpath or on an absolute path.
     * @param path the path.
     * @return the File.
     */
    @JvmStatic fun getFileClasspathOrOrAbsolutePathResource(path: String): File {
        val p = Paths.get(path)
        return if (!p.isAbsolute)
            getFileClasspathResource(path)
        else p.toFile()
    }

    /**
     * Gets a File from a path found in the classpath.
     * @param path the pathname in the classpath.
     * @return the File.
     * @throws IOException if file not found.
     */
    fun classpathResourceExists(path: String): Boolean {
        val r = InternalResource(path)
        return r.exists
    }

    /**
     * Gets a URI from a path found in the classpath.
     * @param path the pathname in the classpath.
     * @return the File.
     * @throws IOException if file not found.
     */
    fun getUriClasspathResource(path: String): URI {
        val r = InternalResource(path)
        return r.uri
    }

    /**
     * Gets an InputStream from a path found in the classpath.
     * @param path the pathname in the classpath.
     * @return the InputStream.
     * @throws IOException if file not found.
     */
    fun getStreamClasspathResource(path: String): InputStream {
        val r = InternalResource(path)
        return r.inputStream
    }

    /**
     * Gets a path to a resource.
     * @param path The path, can be absolute or classpath resource.
     * @return The path.
     * @throws IOException if file not found.
     */
    fun getClasspathOrAbsolutePath(path: String): String {
        val p = Paths.get(path)
        if (p.isAbsolute) {
            return path
        }
        val r = InternalResource(path)
        return Paths.get(r.uri).toString()
    }

    /**
     * Closes a closeable, ignores any exceptions.
     * @param closeable The closeable to close.
     */
    @JvmStatic fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (@Suppress("SwallowedException") _: IOException) {
            //IGNORE
        }
    }

    class InternalResource(private val path: String) {

        val url: URL? = this::class.java.classLoader.getResource(path)
        val exists = url != null
        val inputStream: InputStream by lazy {
            urlNotNull.openStream()
        }
        val file: File by lazy {
            File(urlNotNull.file)
        }
        val uri: URI by lazy {
            urlNotNull.toURI()
        }

        private val urlNotNull by lazy {
            if (exists) url!! else throw FileNotFoundException(
                "class path resource [$path] cannot be resolved to URL because it does not exist")
        }
    }
}
