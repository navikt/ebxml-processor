package no.nav.emottak.util.crypto

import no.nav.emottak.util.getEnvVar
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.HashMap

//TODO Keystorefiler
private val keystorePath = getEnvVar("KEYSTORE_FILE", "xml/signering_keystore.p12")
private val keystorePass = getEnvVar("KEYSTORE_PWD", "123456789")
private val keystoreType = getEnvVar("KEYSTORE_TYPE", "PKCS12")

internal val keyStoreUtil = KeyStoreUtil()
fun getSignerCertificate(alias: String) = keyStoreUtil.getCertificate(alias)
fun getSignerKey(alias: String) = keyStoreUtil.getKey(alias)
fun getCertificateAlias(certificate: X509Certificate) = keyStoreUtil.getCertificateAlias(certificate)
fun getKeyPair(alias: String) = KeyPair(getSignerCertificate(alias).publicKey, getSignerKey(alias))

fun getDekrypteringKey(alias: String) = keyStoreUtil.getKey(alias)
fun getPrivateCertificates() = keyStoreUtil.getPrivateCertificates()

internal class KeyStoreUtil {

    private val keyStore = getKeyStoreResolver()

    internal fun getKey(alias: String) = keyStore.getKey(alias, keystorePass.toCharArray()) as PrivateKey

    internal fun getCertificateAlias(certificate: X509Certificate) = keyStore.getCertificateAlias(certificate)

    internal fun getCertificate(alias: String): X509Certificate {
        return keyStore.getCertificate(alias) as X509Certificate
    }

    internal fun getPrivateCertificates(): Map<String, X509Certificate> {
        val certificates: MutableMap<String, X509Certificate> = HashMap()
        keyStore.aliases().iterator().forEach { alias ->
            if (hasPrivateKeyEntry(alias)) {
                certificates[alias] = keyStore.getCertificate(alias) as X509Certificate
            }
        }
        return certificates
    }

    private fun getKeyStoreResolver(): KeyStore {
        val keyStore = KeyStore.getInstance(keystoreType)
        val fileContent =
            try {
                FileInputStream(keystorePath)
            } catch (e: FileNotFoundException) {
                //TODO Kast exception om keystore ikke kan leses
                ByteArrayInputStream(this::class.java.classLoader.getResource("xml/signering_keystore.p12").readBytes())
            }
        keyStore!!.load(fileContent, keystorePass.toCharArray())
        return keyStore
    }

    private fun hasPrivateKeyEntry(alias: String): Boolean {
        if (keyStore.isKeyEntry(alias)) {
            val key = keyStore.getKey(alias, keystorePass.toCharArray())
            if (key is PrivateKey) {
                return true
            }
        }
        return false
    }
}
