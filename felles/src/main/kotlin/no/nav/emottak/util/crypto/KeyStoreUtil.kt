package no.nav.emottak.util.crypto

import no.nav.emottak.util.getEnvVar
import no.nav.emottak.util.isSelfSigned
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.util.HashMap
import no.nav.emottak.crypto.log

//TODO Keystorefiler
/*
private val keystorePath = getEnvVar("KEYSTORE_FILE", "xml/signering_keystore.p12")
private val keystorePwd = getEnvVar("KEYSTORE_PWD", "123456789")
private val keystoreType = getEnvVar("KEYSTORE_TYPE", "PKCS12")

private val truststorePath = getEnvVar("TRUSTSTORE_PATH", "truststore.p12")
private val truststorePwd = getEnvVar("TRUSTSTORE_PWD", "123456789")

internal val keyStoreUtil = KeyStoreUtil()

fun getTrustedRootCerts() = keyStoreUtil.getTrustedRootCerts()
fun getIntermediateCerts() = keyStoreUtil.getIntermediateCerts()

internal class KeyStoreUtil {

    init {
        Security.addProvider(BouncyCastleProvider());
    }

    private val keyStore = getKeyStoreResolver(keystorePath, keystorePwd.toCharArray())
    private val trustStore = getKeyStoreResolver(truststorePath, truststorePwd.toCharArray())

    internal fun getKey(alias: String) = keyStore.getKey(alias, keystorePwd.toCharArray()) as PrivateKey

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

    private fun getKeyStoreResolver(storePath: String, storePass: CharArray): KeyStore {
        val keyStore = KeyStore.getInstance(keystoreType)
        val fileContent =
            try {
                FileInputStream(storePath)
            } catch (e: FileNotFoundException) {
                //TODO Kast exception om keystore ikke kan leses
                log.error("Unable to load keystore $storePath falling back to truststore",e)
                ByteArrayInputStream(this::class.java.classLoader.getResource("truststore.p12").readBytes())
            }
        keyStore!!.load(fileContent, storePass)
        return keyStore
    }

    private fun hasPrivateKeyEntry(alias: String): Boolean {
        if (keyStore.isKeyEntry(alias)) {
            val key = keyStore.getKey(alias, keystorePwd.toCharArray())
            if (key is PrivateKey) {
                return true
            }
        }
        return false
    }

    internal fun getTrustedRootCerts(): Set<X509Certificate> {
        return getTrustStoreCertificates().filter { isSelfSigned(it) }.toSet()
    }

    internal fun getIntermediateCerts(): Set<X509Certificate> {
        return getTrustStoreCertificates().filter { !isSelfSigned(it) }.toSet()
    }

    private fun getTrustStoreCertificates(): Set<X509Certificate> {
        return trustStore.aliases().toList().map { alias -> trustStore.getCertificate(alias) as X509Certificate }.toSet()
    }
}

*/