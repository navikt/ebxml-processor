package no.nav.emottak.crypto

import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.util.HashMap
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import java.io.File

internal val log = LoggerFactory.getLogger("no.nav.emottak.crypto.KeyStore")
interface KeyStoreConfig {
    val keystorePath:String
    val keyStorePwd:String
    val keyStoreStype:String
}

class KeyStore(private val keyStoreConfig: KeyStoreConfig) {

    private val keyStore = getKeyStoreResolver(keyStoreConfig.keystorePath, keyStoreConfig.keyStorePwd.toCharArray())


    init {
        Security.addProvider(BouncyCastleProvider());
    }


    private fun getKeyStoreResolver(storePath: String, storePass: CharArray): KeyStore {
        val keyStore = KeyStore.getInstance(keyStoreConfig.keyStoreStype)
        val fileContent =
            try {
                log.debug("Getting store file from $storePath")
                if (File(storePath).exists()) {
                    log.info("Getting store file from file <$storePath>")
                    FileInputStream(storePath)
                } else {
                    log.info("Getting store file from resources <$storePath>")
                    ByteArrayInputStream(this::class.java.classLoader.getResourceAsStream(storePath).readBytes())
                }
            } catch (e: Exception) {
                log.error("Failed to load keystore $storePath", e)
                throw RuntimeException("Failed to load keystore $storePath", e)
            }
        keyStore!!.load(fileContent, storePass)
        return keyStore
    }


    fun getPrivateCertificates(): Map<String, X509Certificate> {
        val certificates: MutableMap<String, X509Certificate> = HashMap()
        keyStore.aliases().iterator().forEach { alias ->
            if (hasPrivateKeyEntry(alias)) {
                certificates[alias] = keyStore.getCertificate(alias) as X509Certificate
            }
        }
        return certificates
    }

    fun getPublicCertificates() : Map<String, X509Certificate> {
         val certificates: MutableMap<String, X509Certificate> = HashMap()
         keyStore.aliases().iterator().forEach { alias ->
                if (!hasPrivateKeyEntry(alias)) {
                    certificates[alias] = keyStore.getCertificate(alias) as X509Certificate
                }
            }
            return certificates
    }

    fun getCertificateAlias(certificate: X509Certificate) = keyStore.getCertificateAlias(certificate)

    fun getKeyPair(alias: String) = KeyPair(getCertificate(alias).publicKey, getKey(alias))

    fun getCertificate(alias: String): X509Certificate {
        return keyStore.getCertificate(alias) as X509Certificate
    }

    private fun hasPrivateKeyEntry(alias: String): Boolean {
        if (keyStore.isKeyEntry(alias)) {
            val key = keyStore.getKey(alias, keyStoreConfig.keyStorePwd.toCharArray())
            if (key is PrivateKey) {
                return true
            }
        }
        return false
    }

    fun getKey(alias: String) = keyStore.getKey(alias, keyStoreConfig.keyStorePwd.toCharArray()) as PrivateKey



}