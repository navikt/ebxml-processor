package no.nav.emottak.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate

internal val log = LoggerFactory.getLogger("no.nav.emottak.crypto.KeyStore")

interface KeyStoreConfig {
    val keyStoreFile: InputStream
    val keyStorePass: CharArray
    val keyStoreType: String
}

class KeyStore(private val keyStoreConfig: KeyStoreConfig) {

    private val keyStore: KeyStore

    init {
        Security.addProvider(BouncyCastleProvider())
        keyStore = keyStoreResolver()
    }

    private fun keyStoreResolver(): KeyStore {
        val keyStore = KeyStore.getInstance(keyStoreConfig.keyStoreType)
        keyStore!!.load(keyStoreConfig.keyStoreFile, keyStoreConfig.keyStorePass)
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
            val key = keyStore.getKey(alias, keyStoreConfig.keyStorePass)
            if (key is PrivateKey) {
                return true
            }
        }
        return false
    }

    fun getKey(alias: String) = keyStore.getKey(alias, keyStoreConfig.keyStorePass) as PrivateKey

}
