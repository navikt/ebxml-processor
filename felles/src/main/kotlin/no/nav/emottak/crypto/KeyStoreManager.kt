package no.nav.emottak.crypto

import com.google.common.collect.Iterators
import com.google.common.collect.Iterators.asEnumeration
import java.io.InputStream
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Enumeration
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory


internal val log = LoggerFactory.getLogger(KeyStoreManager::class.java)

interface KeyStoreConfig {
    val keyStoreFile: InputStream
    val keyStorePass: CharArray
    val keyStoreType: String
}

class KeyStoreManager(private vararg val keyStoreConfig: KeyStoreConfig) {
    private val keyStores: List<Pair<KeyStore, KeyStoreConfig>>
    init {
        Security.addProvider(BouncyCastleProvider())
        keyStores = keyStoreResolver()

    }

    private fun keyStoreResolver(): List<Pair<KeyStore, KeyStoreConfig>> {
        return keyStoreConfig.map { config ->
            Pair<KeyStore, KeyStoreConfig>(
                KeyStore.getInstance(config.keyStoreType)
                    .apply {
                        load(config.keyStoreFile, config.keyStorePass)
                    }, config
            )
        }.toList()
    }

    fun List<Pair<KeyStore, KeyStoreConfig>>.aliases(): Enumeration<String> {
        return asEnumeration(
            Iterators.concat(
                this.map { it.first.aliases().iterator() }.iterator()
            )
        )
    }

    fun getPrivateCertificates(): Map<String, X509Certificate> {
        val certificates: MutableMap<String, X509Certificate> = HashMap()
        keyStores.forEach { (store) ->
            store.aliases().iterator().forEach { alias ->
                if (hasPrivateKeyEntry(alias)) {
                    certificates[alias] = store.getCertificate(alias) as X509Certificate
                }
            }
        }
        return certificates
    }

    fun getPublicCertificates(): Map<String, X509Certificate> {
        val certificates: MutableMap<String, X509Certificate> = HashMap()
        keyStores.aliases().iterator().forEach { alias ->
            if (hasCertEntry(alias)) {
                certificates[alias] = getCertificate(alias)
            }
        }
        return certificates
    }

    fun getCertificateAlias(certificate: X509Certificate) = keyStores.firstNotNullOf { (store) -> store.getCertificateAlias(certificate) }

    fun getKeyPair(alias: String) = KeyPair(getCertificate(alias).publicKey, getKey(alias))

    fun getCertificate(alias: String): X509Certificate {
        return keyStores.firstNotNullOf { (store) -> store.getCertificate(alias) as X509Certificate }
    }

    fun aliases(): Enumeration<String> {
        return keyStores.aliases()
    }

    fun getCertificateChain(alias: String): Array<X509CertificateHolder> {
        return keyStores
            .firstNotNullOfOrNull { (store) -> store.getCertificateChain(alias) }
            ?.filterIsInstance<X509Certificate>()?.map { JcaX509CertificateHolder(it) }?.toTypedArray()
            ?: emptyArray()
    }

    private fun hasCertEntry(alias: String): Boolean {
        return keyStores.any { (store) ->
            store.isCertificateEntry(alias) && store.getCertificate(alias) is X509Certificate
        }
    }

    private fun hasPrivateKeyEntry(alias: String): Boolean {
        return keyStores.any { (store, config) ->
            store.isKeyEntry(alias) && store.getKey(alias, config.keyStorePass) is PrivateKey
        }
    }

    fun getKey(alias: String) =
        keyStores.first { (store) -> store.isKeyEntry(alias) }
            .let { (store, config) -> store.getKey(alias, config.keyStorePass) } as PrivateKey

}
