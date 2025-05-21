package no.nav.emottak.payload.helseid.testutils

import no.nav.emottak.payload.helseid.testutils.ResourceUtil
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Collections

@Suppress("TooManyFunctions")
object SecurityUtils {

    /**
     * Creates a [KeyStore] from a path.
     * @param path The path to the keystore.
     * @param type The keystore type
     * @param pwd The password.
     * @return The keystore.
     */
    fun createKeyStore(path: String, type: String = "jks", pwd: CharArray): KeyStore = try {
        createKeyStore(ResourceUtil.getByteArrayClasspathOrAbsolutePathResource(path, true), type, pwd)
    } catch (e: IOException) {
        throw SecurityException("Failed to find keystore $path", e)
    }

    /**
     * Creates a [KeyStore] from a byte array.
     * @param bytes The bytes representing the keystore.
     * @param type The keystore type
     * @param pwd The password.
     * @return The keystore.
     */
    fun createKeyStore(bytes: ByteArray, type: String = "jks", pwd: CharArray): KeyStore = try {
        ByteArrayInputStream(bytes).use { bais ->
            val ks = KeyStore.getInstance(type)
            ks.load(bais, pwd)
            ks
        }
    } catch (e: IOException) {
        throw SecurityException("Failed to load the keystore", e)
    } catch (e: GeneralSecurityException) {
        throw SecurityException("Failed to load the keystore", e)
    }

    /**
     * Gets a private key from the keystore.
     * @param keyStore The keystore.
     * @param password The key's password.
     * @param alias The alias.
     * @return The key.
     */
    fun getPrivateKey(keyStore: KeyStore, password: CharArray, alias: String): PrivateKey {
        return try {
            if (keyStore.isKeyEntry(alias)) {
                keyStore.getKey(alias, password) as PrivateKey
            } else {
                throw SecurityException("Key with alias $alias not found.")
            }
        } catch (e: GeneralSecurityException) {
            throw SecurityException("Failed to get key with alias $alias from keystore.", e)
        }
    }

    /**
     * Gets a certificate from the keystore.
     * @param keyStore The keystore.
     * @param alias The alias.
     * @return The certificate.
     */
    fun getCertificate(keyStore: KeyStore, alias: String): X509Certificate = try {
        val c = keyStore.getCertificate(alias)
        if (c != null) {
            c as X509Certificate
        } else {
            throw SecurityException("Certificate with alias $alias not found")
        }
    } catch (e: KeyStoreException) {
        throw SecurityException("Failed to get certificate with alias $alias from keystore.", e)
    }

    /**
     * Gets aliases.
     * @param keyStore The [KeyStore].
     * @return List of all aliases.
     */
    fun getAliases(keyStore: KeyStore): List<String> {
        return try {
            Collections.list(keyStore.aliases())
        } catch (e: KeyStoreException) {
            throw SecurityException("failed to get aliases", e)
        }
    }

    /**
     * Adds a public certificate.
     * @param keyStore The keystore.
     * @param alias The alias.
     * @param certificate The certificate.
     */
    fun add(keyStore: KeyStore, alias: String, certificate: X509Certificate) {
        try {
            keyStore.setCertificateEntry(alias, certificate)
        } catch (e: KeyStoreException) {
            throw SecurityException("failed to add certificate", e)
        }
    }

    /**
     * Adds a key pair.
     * @param keyStore The keystore.
     * @param keystorePassword The keystore password.
     * @param alias The alias for the entry.
     * @param bytes The private key pair, pkcs12.
     * @param certificatePassword The password protecting the key pair.
     */
    fun add(
        keyStore: KeyStore,
        keystorePassword: CharArray,
        alias: String,
        bytes: ByteArray,
        certificatePassword: CharArray
    ) {
        try {
            ByteArrayInputStream(bytes).use { bais ->
                val p12 = KeyStore.getInstance("pkcs12")
                p12.load(bais, certificatePassword)
                val keyAlias = getKeyAlias(p12)
                val key = p12.getKey(keyAlias, certificatePassword)
                keyStore.setKeyEntry(alias, key, keystorePassword, p12.getCertificateChain(keyAlias))
            }
        } catch (e: GeneralSecurityException) {
            throw SecurityException("failed to add certificate with private key", e)
        }
    }

    /**
     * gets the alias which is the key entry
     * @param p12 The keystore.
     * @return The alias for the key.
     * @throws KeyStoreException if it fails.
     */
    private fun getKeyAlias(p12: KeyStore): String {
        for (alias in getAliases(p12)) {
            if (p12.isKeyEntry(alias)) {
                return alias
            }
        }
        throw SecurityException("No private keys in keystore")
    }
}
