package no.nav.emottak.payload.helseid.util.security

import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.bouncycastle.openssl.PEMEncryptedKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.bc.BcPEMDecryptorProvider
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo
import org.bouncycastle.pkcs.PKCSException
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.StringReader
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Collections
import no.nav.emottak.payload.helseid.util.util.EnvironmentInitializer
import no.nav.emottak.payload.helseid.util.util.EnvironmentInitializer.isDotDot
import no.nav.emottak.payload.helseid.util.util.KeystoreProperties
import no.nav.emottak.payload.helseid.util.util.ResourceUtil
import org.slf4j.LoggerFactory


@Suppress("TooManyFunctions")
object SecurityUtils {
    /**
     * Gets a [KeyPair] from PKCS12 certificate file (keystore).
     * @param bytes The [KeyStore] byte array
     * @param pwd The password.
     * @return The [KeyPair].
     */
    fun getKeyPair(bytes: ByteArray, pwd: CharArray): KeyPair =
        getKeyPair(createKeyStore(bytes, "PKCS12", pwd), pwd)

    /**
     * Gets a [KeyPair] from PKCS12 certificate file (keystore).
     * @param p12 The [KeyStore]
     * @param pwd The password.
     * @return The [KeyPair].
     */
    fun getKeyPair(p12: KeyStore, pwd: CharArray): KeyPair =
        try {
            val keyAlias = getKeyAlias(p12)
            val cert = p12.getCertificate(keyAlias) as X509Certificate
            KeyPair(cert.publicKey, getPrivateKey(p12, pwd, keyAlias))
        } catch (e: KeyStoreException) {
            throw SecurityException("Failed to get KeyPair", e)
        }

    /**
     * Creates a new [KeyStore] containing certificates public keys read from a certificate file,
     * and private and public keys read from pkcs12 files and a json files containing
     * the passwords. A side effect: stores the keystore on file (KeystoreProperties.path).     *
     * @param kp The keystore properties
     * @param directory directory containing public certificate files and virksomhets certificates properties json files
     * @param rejectInvalid if true we abort if any of the certificates are invalid
     * (today must be between certificates notBefore and notAfter), defaults to true
     * @return the keystore
     */
    fun createKeyStore(
        kp: KeystoreProperties,
        directory: String,
        rejectInvalid: Boolean = true
    ): KeyStore {
        val keystore = createNewKeystore(path = kp.path, password = kp.password, type = kp.type)
        add(keystore, directory, rejectInvalid)
        add(keystore, directory, kp.password, rejectInvalid)
        store(keystore, kp.password, kp.path)
        return keystore
    }

    /**
     * creates a new empty keystore
     * @param path the path to the keystore
     * @param password the keystore password
     * @param type the keystore type
     */
    fun createNewKeystore(path: String, password: CharArray, type: String = "JKS"): KeyStore {
        File(path).createNewFile()
        val keystore = KeyStore.getInstance(type)
        keystore.load(null, password)
        store(keystore, password, path)
        return keystore
    }

    private fun addCertificate(
        caek: CertificateAndEncryptedKey, keystore: KeyStore,
        password: CharArray, rejectInvalid: Boolean
    ) {
        add(keystore, password, caek.alias, caek.x509Certificate, caek.privateKey)
        validate(keystore, caek.alias, caek.keyUsage, caek.serialNo, rejectInvalid)
    }

    @Suppress("MaxLineLength")
    private fun validate(
        keystore: KeyStore, alias: String, keyUsage: KeyUsage,
        serialNo: BigInteger?, rejectInvalid: Boolean
    ) {
        val cert = getCertificate(keystore, alias)
        require(X509Utils.hasKeyUsage(cert, keyUsage)) {
            "the private certificate with alias $alias does not have the required keyUsage $keyUsage, but has these: ${
                X509Utils.getKeyUsage(
                    cert
                )
            }"
        }
        require(serialNo == null || cert.serialNumber == serialNo) {
            "the private certificate with alias $alias does not have the required serialNumber $serialNo, serial number is ${cert.serialNumber}"
        }
        if (rejectInvalid) {
            require(X509Utils.isValid(cert)) { "the private certificate with alias $alias is invalid, notAfter = ${cert.notAfter}" }
        }
    }

    /**
     * Creates a [KeyStore] from a path.
     * @param path The path to the keystore.
     * @param type The keystore type
     * @param pwd The password.
     * @return The keystore.
     */
    fun createKeyStore(path: String, type: String = "jks", pwd: CharArray): KeyStore =
        try {
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
    fun createKeyStore(bytes: ByteArray, type: String = "jks", pwd: CharArray): KeyStore =
        try {
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
     * gets a certificate and it's private key from a path
     * @param path the path to the pem encoded encrypted key and certificate
     * @return a pair of the certificate and the private key
     */
    fun getCertificateAndPrivateKeyPair(path: String): Pair<X509Certificate, PrivateKey> {
        val caek = deserializeCertificateAndEncryptedKey(path)
        return caek.x509Certificate to caek.privateKey
    }

    /**
     * Gets a private key from the keystore with the given serial number
     * @param keyStore The keystore.
     * @param password The key's password.
     * @param serialNumber The serial number
     * @param radix the radix of the serial number (default 10)
     * @return The key.
     */
    fun getPrivateKeyWithSerialNumber(
        keyStore: KeyStore, password: CharArray,
        serialNumber: String, radix: Int = 10
    ): PrivateKey =
        getPrivateKey(keyStore, password) { it.serialNumber.toString(radix) == serialNumber }

    /**
     * Gets a private key from the keystore based with the given thumbprint
     * @param keyStore The keystore.
     * @param password The key's password.
     * @param thumbprint The thumbprint
     * @param algorithm the hashing algorithm (default SHA-256)
     * @return The key.
     */
    fun getPrivateKeyWithThumbprint(
        keyStore: KeyStore, password: CharArray,
        thumbprint: String, algorithm: String = SHA256
    ): PrivateKey =
        getPrivateKey(keyStore, password) { X509Utils.thumbprintBase64Url(it, algorithm) == thumbprint }


    /**
     * Gets a certificate from the keystore.
     * @param keyStore The keystore.
     * @param alias The alias.
     * @return The certificate.
     */
    fun getCertificate(keyStore: KeyStore, alias: String): X509Certificate =
        try {
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
     * Gets an alias from the keystore with the given thumbprint
     * @param keyStore The keystore.
     * @param thumbprint The thumbprint
     * @param algorithm The hashing algorithm (default SHA-256)
     * @return The certificate.
     */
    fun getAliasWithThumbprint(
        keyStore: KeyStore,
        thumbprint: String, algorithm: String = SHA256
    ): String =
        getAlias(keyStore) { X509Utils.thumbprintBase64Url(it, algorithm) == thumbprint }

    /**
     * Gets a certificate from the keystore with the given serial number
     * @param keyStore The keystore.
     * @param serialNumber The serial number
     * @param radix the radix of the serial number (default 10)
     * @return The certificate.
     */
    fun getCertificateWithSerialNumber(keyStore: KeyStore, serialNumber: String, radix: Int = 10): X509Certificate =
        getCertificate(keyStore) { it.serialNumber.toString(radix) == serialNumber }

    /**
     * Gets a certificate from the keystore with the given thumbprint
     * @param keyStore The keystore.
     * @param thumbprint The thumbprint
     * @param algorithm The hashing algorithm (default SHA-256)
     * @return The certificate.
     */
    fun getCertificateWithThumbprint(
        keyStore: KeyStore,
        thumbprint: String, algorithm: String = SHA256
    ): X509Certificate =
        getCertificate(keyStore) { X509Utils.thumbprintBase64Url(it, algorithm) == thumbprint }

    /**
     * gets a certificate chain from the keystore
     * @param keyStore The keystore.
     * @param alias The alias.
     * @return The certificate chain.
     */
    fun getCertificateChain(keyStore: KeyStore, alias: String): Collection<X509Certificate> =
        try {
            if (keyStore.containsAlias(alias)) {
                val chain = keyStore.getCertificateChain(alias)
                chain?.indices?.map { chain[it] as X509Certificate }.orEmpty()
            } else {
                throw SecurityException("KeyStore does not contain alias $alias")
            }
        } catch (e: KeyStoreException) {
            throw SecurityException("Failed to get certificate chain", e)
        }

    /**
     * gets the aliases of the certificates where we have the private key.
     * @param keyStore The keystore.
     * @param password The keystore password.
     * @return a collection of aliases.
     */
    fun getPrivateAliases(keyStore: KeyStore, password: CharArray): Collection<String> =
        getAliases(keyStore).filter { hasPrivateKey(keyStore, password, it) }

    /**
     * Checks if we have a private key for a certificate.
     * @param keyStore The keystore.
     * @param pwd The password.
     * @param alias The alias.
     * @return true or false;
     */
    fun hasPrivateKey(keyStore: KeyStore, pwd: CharArray, alias: String): Boolean {
        return try {
            keyStore.isKeyEntry(alias) && keyStore.getKey(alias, pwd) is PrivateKey
        } catch (e: GeneralSecurityException) {
            throw SecurityException("Failed to get private key", e)
        }
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
     * Deletes a public certificate.
     * @param keyStore The keystore.
     * @param alias The alias.
     */
    fun delete(keyStore: KeyStore, alias: String) {
        try {
            keyStore.deleteEntry(alias)
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
        keyStore: KeyStore, keystorePassword: CharArray, alias: String,
        bytes: ByteArray, certificatePassword: CharArray
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
     * Adds a key pair.
     * @param keyStore The keystore.
     * @param keystorePassword The keystore password.
     * @param alias The alias for the entry.
     * @param certificate The public certificate
     * @param privateKey The private key
     */
    fun add(
        keyStore: KeyStore, keystorePassword: CharArray, alias: String,
        certificate: X509Certificate, privateKey: PrivateKey
    ) {
        try {
            keyStore.setKeyEntry(alias, privateKey, keystorePassword, arrayOf(certificate))
        } catch (e: GeneralSecurityException) {
            throw SecurityException("failed to add certificate with private key", e)
        }
    }

    /**
     * Adds public certificates from files with .cer extension from a directory.
     * The filename without extension will be the alias.
     * Files with .cerlist extension will be read (contains a list of alias and pem-encoded x509 certificates)
     * and written to the same directory as pem-encoded x509 files with the filename as alias.cer and loaded
     * into the keystore together with the rest of the .cer files.
     * @param keyStore The keystore
     * @param directory The directory
     * @param rejectInvalid if true we abort if any of the certificates are invalid
     */
    fun add(keyStore: KeyStore, directory: String, rejectInvalid: Boolean = true) {
        val dir = ResourceUtil.getFileClasspathOrOrAbsolutePathResource(directory)
        if (dir.isDirectory) {
            // first we read collections of alias and pem-encoded certificate pairs
            // and write these to disk
            dir.walk()
                .filter { f -> f.name.matches(CERTIFICATE_LIST_REGEX) }
                .filterNot { isDotDot(it) }
                .forEach { f ->
                    val certs: Collection<PublicCertificate> = readCertificateList(f)
                    certs.forEach {
                        File("$dir/${it.alias}.cer").writeText(it.pem)
                    }
                }

            // then we read all the pem-encoded certificates and load them into the keystore
            dir.walk()
                .filter { f -> f.name.matches(CERTIFICATE_FILENAME_REGEX) }
                .filterNot { isDotDot(it) }
                .forEach { f ->
                    val fname = f.name
                    val alias = fname.substring(0, fname.lastIndexOf('.'))
                    val certificate = X509Utils.loadCertificate(f.readBytes())
                    if (rejectInvalid)
                        require(X509Utils.isValid(certificate)) {
                            "the public certificate with alias $alias is invalid, notAfter = ${certificate.notAfter}"
                        }
                    LOGGER.info("adding alias $alias to keystore")
                    keyStore.setCertificateEntry(alias, certificate)
                }
        }
    }

    /**
     * reads a json file with aliases and certificates
     */
    fun readCertificateList(file: File): Collection<PublicCertificate> =
        mapper.readValue(file.readBytes())

    /**
     * Adds private certificates from files with .enckey extension from a directory.
     * @param keyStore The keystore
     * @param directory The directory
     * @param password The keystore password
     * @param rejectInvalid if true we abort if any of the certificates are invalid
     */
    fun add(keyStore: KeyStore, directory: String, password: CharArray, rejectInvalid: Boolean = true) {
        val dir = ResourceUtil.getFileClasspathOrOrAbsolutePathResource(directory)
        if (dir.isDirectory) {
            dir.walk()
                .filter { f -> f.name.matches(ENCRYPTED_KEY_FILENAME_REGEX) }
                .filterNot { isDotDot(it) }
                .forEach { f ->
                    val certificateAndEncryptedKey = mapper.readValue<CertificateAndEncryptedKey>(f)
                    addCertificate(certificateAndEncryptedKey, keyStore, password, rejectInvalid)
                }
        }
    }

    /**
     * Stores the keystore.
     * @param keyStore The keystore.
     * @param password The password.
     * @param path Where to store the keystore.
     */
    @Suppress("TooGenericExceptionCaught")
    fun store(keyStore: KeyStore, password: CharArray, path: String) {
        try {
            FileOutputStream(path).use { keyStore.store(it, password) }
        } catch (e: Exception) {
            throw SecurityException("failed to store keystore", e)
        }
    }

    /**
     * gets KeystoreProperties a path to certificate and encrypted private key
     * @param path the path to the json file
     * @return the KeystoreProperties
     */
    fun getKeystoreProperties(path: String): KeystoreProperties {
        val caek = deserializeCertificateAndEncryptedKey(path)
        val ks = caek.createKeyStore()
        val kp = KeystoreProperties(caek.keystorePath, type = "JKS", password = caek.password.toCharArray())
        store(ks, kp.password, kp.absolutePath)
        return kp
    }

    /**
     * deserializes a file containing a pem encoded encrypted key and an X509Certificate
     * @param path the path to the file
     * @return a CertificateAndEncryptedKey object
     */
    fun deserializeCertificateAndEncryptedKey(path: String): CertificateAndEncryptedKey = jacksonObjectMapper()
        .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true)
        .readValue<CertificateAndEncryptedKey>(ResourceUtil.getByteArrayClasspathOrAbsolutePathResource(path))

    private fun getAlias(keyStore: KeyStore, predicate: (X509Certificate) -> Boolean): String =
        try {
            keyStore.aliases().asSequence().forEach {
                val cert = getCertificate(keyStore, it)
                if (predicate(cert)) {
                    return it
                }
            }
            throw SecurityException("Alias not found")
        } catch (e: KeyStoreException) {
            throw SecurityException("Failed to get alias", e)
        }

    private fun getCertificate(keyStore: KeyStore, predicate: (X509Certificate) -> Boolean): X509Certificate =
        try {
            keyStore.aliases().asSequence().forEach {
                val cert = getCertificate(keyStore, it)
                if (predicate(cert)) {
                    return cert
                }
            }
            throw SecurityException("Certificate not found")
        } catch (e: KeyStoreException) {
            throw SecurityException("Failed to get certificate", e)
        }

    private fun getPrivateKey(
        keyStore: KeyStore, password: CharArray,
        predicate: (X509Certificate) -> Boolean
    ): PrivateKey =
        try {
            getPrivateAliases(keyStore, password).forEach {
                val cert = getCertificate(keyStore, it)
                if (predicate(cert)) {
                    return keyStore.getKey(it, password) as PrivateKey
                }
            }
            throw SecurityException("Private key not found")
        } catch (e: KeyStoreException) {
            throw SecurityException("Failed to get private key", e)
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

    private const val SHA256 = "SHA-256"
    private val CERTIFICATE_FILENAME_REGEX = Regex(".*\\.cer$")
    private val ENCRYPTED_KEY_FILENAME_REGEX = Regex(".*\\.enckey$")
    internal val CERTIFICATE_LIST_REGEX = Regex(".*\\.cerlist$")
    private val mapper = jacksonObjectMapper()
        .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true)
    private val LOGGER = LoggerFactory.getLogger("no.nav.emottak.payload.helseid.util.security.SecurityUtils")
}

data class PublicCertificate(val alias: String, val pem: String)

data class CertificateAndEncryptedKey(
    val alias: String,
    val password: String,
    val certificate: String,
    val encryptedKey: String,
    val keyUsage: KeyUsage = KeyUsage.NON_REPUDIATION,
    val serialNo: BigInteger? = null
) {

    val x509Certificate: X509Certificate by lazy {
        X509Utils.loadCertificate(certificate.toByteArray())
    }

    val privateKey: PrivateKey by lazy {
        getEncryptedKey()
    }

    val keystorePath: String by lazy {
        "/tmp/${x509Certificate.serialNumber}.jks"
    }

    fun createKeyStore(): KeyStore {
        val ks = SecurityUtils.createNewKeystore(path = keystorePath, password = password.toCharArray())
        SecurityUtils.add(
            keyStore = ks, keystorePassword = password.toCharArray(),
            alias = alias, certificate = x509Certificate, privateKey = privateKey
        )
        return ks
    }

    private fun getEncryptedKey(): PrivateKey {
        PEMParser(StringReader(encryptedKey)).use { pemParser ->
            val pki = when (val o = pemParser.readObject()) {
                is PKCS8EncryptedPrivateKeyInfo -> {
                    val builder = JcePKCSPBEInputDecryptorProviderBuilder()
                    val idp = builder.build(password.toCharArray())
                    o.decryptPrivateKeyInfo(idp)
                }

                is PEMEncryptedKeyPair -> {
                    val pkp = o.decryptKeyPair(BcPEMDecryptorProvider(password.toCharArray()))
                    pkp.privateKeyInfo
                }

                else -> {
                    throw PKCSException("Invalid encrypted private key class: " + o.javaClass.name)
                }
            }
            val converter = JcaPEMKeyConverter()
            return converter.getPrivateKey(pki)
        }
    }

}
