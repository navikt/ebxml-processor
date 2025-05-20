package no.nav.emottak.payload.helseid.util.util

import java.io.File
import java.util.Base64

object EnvironmentInitializer {

    fun initialize(doSystemProperties: Boolean = true, doCredentials: Boolean = true, doBase64: Boolean = true) {
        val dirs = SystemUtil.getEnvOrSystemProperty(DIR_ENV, DEFAULT_DIRECTORIES)
        if (doSystemProperties) setSystemProperties(dirs)
        if (doCredentials) setCredentials(dirs)
        if (doBase64) base64Decode(dirs)
    }

    private fun setSystemProperties(dirs: String) {
        doWithExtension(dirs, "env") { f ->
            f.readLines()
                .filterNot { it.startsWith('#') }
                .forEach { l ->
                    val keyValue = l.split('=', limit = 2)
                    println("setting system property ${keyValue[0]}")
                    System.setProperty(keyValue[0], keyValue[1])
                }
        }
    }

    private fun base64Decode(dirs: String) {
        doWithExtension(dirs, "b64") { f ->
            println("decoding file ${f.absolutePath}")
            File(f.absolutePath.dropLast(B64_EXT_LENGTH)).writeBytes(Base64.getDecoder().decode(f.readBytes()))
        }
    }

    private fun setCredentials(dirs: String) {
        doWithDirs(dirs) { d ->
            d.walk()
                .filterNot { isDotDot(it) }
                .filter { it.name.startsWith("credential_") }
                .forEach { f ->
                    val prefix = f.name.removePrefix("credential_")
                    credential("${f.path}/username", prefix, "USERNAME")
                    credential( "${f.path}/password", prefix, "PASSWORD")
                }
        }
    }

    private fun credential(path: String, prefix: String, what: String) {
        System.setProperty("${prefix}_$what", ResourceUtil.getFileClasspathOrOrAbsolutePathResource(path).readText())
        println("setting system property ${prefix}_$what")
    }

    private fun doWithExtension(dirs: String, ext: String, lambda: (dir: File) -> Unit) {
        doWithDirs(dirs) { d ->
            d.walk()
                .filter { it.extension == ext}
                .filterNot { isDotDot(it) }
                .forEach { f -> lambda.invoke(f) }
        }
    }

    private fun doWithDirs(dirs: String, lambda: (dir: File) -> Unit) {
        dirs.split(',')
            .map { ResourceUtil.getFileClasspathOrOrAbsolutePathResource(it) }
            .filterNot { isDotDot(it) }
            .forEach {
                lambda.invoke(it)
            }
    }

    /**
     * used to filter out the files and directories created by k8s secrets manager, like
     * @param f the file
     * @return true if the file name contains two dots
     */
    fun isDotDot(f: File) = f.path.contains("..")

    private const val DEFAULT_DIRECTORIES = "/var/run/secrets/nais.io"
    internal const val DIR_ENV = "ERESEPT_INIT_FILES"
    private const val B64_EXT_LENGTH = 4

}
