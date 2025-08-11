package no.nav.emottak.payload.helseid.testutils

import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.PrivateKey
import java.security.cert.X509Certificate

@Suppress("TooManyFunctions")
object SecurityUtils {

    fun createKeyStore(path: String, type: String = "jks", pwd: CharArray): KeyStore = try {
        createKeyStore(ResourceUtil.getByteArrayClasspathOrAbsolutePathResource(path, true), type, pwd)
    } catch (e: IOException) {
        throw SecurityException("Failed to find keystore $path", e)
    }

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
}
