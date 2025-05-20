package no.nav.emottak.payload.helseid.util.util

import java.io.IOException

private const val PATH_REGEX = ".*[\\\\/]"

data class KeystoreProperties(
    var path: String = "",
    var type: String = "JKS",
    var password: CharArray = CharArray(0)
) {

    val name: String by lazy {
        path.replace(PATH_REGEX.toRegex(), "")
    }

    @Suppress("SwallowedException")
    val absolutePath: String by lazy {
        try {
            ResourceUtil.getFileClasspathResource(path).absolutePath
        } catch (e: IOException) {
            path
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KeystoreProperties

        if (path != other.path) return false
        if (type != other.type) return false
        if (!password.contentEquals(other.password)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + password.contentHashCode()
        return result
    }
}
