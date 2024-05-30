package no.nav.emottak.crypto

import no.nav.emottak.util.getEnvVar
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class FileKeyStoreConfig(
    keyStoreFilePath: String,
    override val keyStorePass: CharArray,
    override val keyStoreType: String = getEnvVar("KEYSTORE_TYPE", "PKCS12")
) : KeyStoreConfig {
    override val keyStoreFile: InputStream = getKeyStoreFile(keyStoreFilePath)

    private fun getKeyStoreFile(path: String): InputStream {
        return try {
            log.debug("Getting store file from $path")
            if (File(path).exists()) {
                log.info("Getting store file from file <$path>")
                FileInputStream(path)
            } else {
                log.info("Getting store file from resources <$path>")
                ByteArrayInputStream(this::class.java.classLoader.getResourceAsStream(path).readBytes())
            }
        } catch (e: Exception) {
            log.error("Failed to load keystore $path", e)
            throw RuntimeException("Failed to load keystore $path", e)
        }
    }
}