package no.nav.emottak.crypto

import com.google.common.collect.Iterators
import com.google.common.collect.Iterators.asEnumeration
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Enumeration
import java.util.function.Predicate.not
import javax.security.auth.x500.X500Principal

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
                        try {
                            load(config.keyStoreFile, config.keyStorePass)
                        } catch (e: Exception) {
                            log.error("Failed to load keystore: ${config.javaClass.name}", e)
                        }
                    },
                config
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

    fun getKeyForIssuer(issuer: X500Principal): Pair<PrivateKey, X509Certificate> {
        log.debug("Checking key for ${issuer.name} ")
        return getPrivateCertificates().filter { (alias, privCert) ->
            issuer.name.split(", ", ",") // "getRdns", litt hacky, fant ikke innebygd funksjonalitet for dette
                .filter { rdn ->
                    !listOf("C=", "L=", "ST=")
                        .any { rdnToIgnore -> rdn.contains(rdnToIgnore, false) }
                }
                .any { rdn ->
                    privCert.issuerX500Principal.name.contains(rdn) // "Loose" matching for key. RDN match for CN not needed
                }
        }.map {
            log.debug("Alias found: ${it.key} for issuer $issuer")
            Pair(getKey(it.key), it.value)
        }.first()
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

    fun getCertificateAlias(certificate: X509Certificate) =
        keyStores.firstNotNullOf { (store) -> store.getCertificateAlias(certificate) }

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

//    fun getKey(certificate: X509Certificate) = keyStores.first {
//        (store) -> store.getKey()
//    }

    fun getKey(alias: String) =
        keyStores.first { (store) -> store.isKeyEntry(alias) }
            .let { (store, config) -> store.getKey(alias, config.keyStorePass) } as PrivateKey
}
