package no.nav.emottak.payload.crypto

import no.nav.emottak.crypto.FileKeyStoreConfig
import no.nav.emottak.crypto.KeyStore
import no.nav.emottak.crypto.VaultKeyStoreConfig
import no.nav.emottak.crypto.parseVaultJsonObject
import no.nav.emottak.util.decodeBase64
import no.nav.emottak.util.getEnvVar
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cms.CMSEnvelopedData
import org.bouncycastle.cms.KeyTransRecipientId
import org.bouncycastle.cms.KeyTransRecipientInformation
import org.bouncycastle.cms.RecipientId
import org.bouncycastle.cms.RecipientInformation
import org.bouncycastle.cms.RecipientInformationStore
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.FileReader
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate

private fun dekrypteringConfig() =
    when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
        "dev-fss" ->
            // Fixme burde egentlig hente fra dev vault context for å matche prod oppførsel
            FileKeyStoreConfig(
                keyStoreFilePath = getEnvVar("KEYSTORE_FILE_DEKRYPT"),
                keyStorePass = getEnvVar("KEYSTORE_PWD").toCharArray(),
                keyStoreType = getEnvVar("KEYSTORE_TYPE", "PKCS12")
            )
        "prod-fss" ->
            VaultKeyStoreConfig(
                keyStoreVaultPath = getEnvVar("VIRKSOMHETSSERTIFIKAT_PATH"),
                keyStoreFileResource = getEnvVar("VIRKSOMHETSSERTIFIKAT_DEKRYPTERING"),
                keyStorePassResource = getEnvVar("VIRKSOMHETSSERTIFIKAT_CREDENTIALS")
            )
        else ->
            FileKeyStoreConfig(
                keyStoreFilePath = getEnvVar("KEYSTORE_FILE_DEKRYPT", "xml/signering_keystore.p12"),
                keyStorePass = FileReader(
                    getEnvVar(
                        "KEYSTORE_PWD_FILE",
                        FileKeyStoreConfig::class.java.classLoader.getResource("keystore/credentials-test.json").path.toString()
                    )
                ).readText().parseVaultJsonObject("password").toCharArray(),
                keyStoreType = getEnvVar("KEYSTORE_TYPE", "PKCS12")
            )
    }

/*
 *
 * 5.15.1 Dekryptering av vedlegg
 */
class Dekryptering(private val keyStore: KeyStore = KeyStore(dekrypteringConfig())) {

    init {
        val provider = BouncyCastleProvider()
        Security.addProvider(provider)
        Security.setProperty("crypto.policy", "unlimited")
    }

    fun dekrypter(byteArray: ByteArray, isBase64: Boolean): ByteArray {
        val bytes = if (isBase64) {
            decodeBase64(byteArray)
        } else {
            byteArray
        }
        try {
            val envelopedData = CMSEnvelopedData(bytes) // Regel ID 263
            val recipients: RecipientInformationStore = envelopedData.recipientInfos
            for (recipient in recipients.recipients as Collection<RecipientInformation?>) {
                if (recipient is KeyTransRecipientInformation) {
                    val key: PrivateKey = getPrivateKeyMatch(recipient)
                    return getDeenvelopedContent(recipient, key)
                }
            }
            throw DecryptionException("Fant ikke PrivateKey for dekryptering med recipients ${recipients.recipients}")
        } catch (e: Exception) {
            throw DecryptionException("Feil ved dekryptering", e)
        }
    }

    private fun getDeenvelopedContent(recipient: RecipientInformation, key: PrivateKey): ByteArray {
        return recipient.getContent(JceKeyTransEnvelopedRecipient(key)) ?: throw DecryptionException("Meldingen er tom.")
    }

    private fun getPrivateKeyMatch(recipient: RecipientInformation): PrivateKey {
        if (recipient.rid.type == RecipientId.keyTrans) {
            val privateCertificates: Map<String, X509Certificate> = keyStore.getPrivateCertificates()
            val rid = recipient.rid as KeyTransRecipientId
            val issuer = rid.issuer
            privateCertificates.entries.filter { (_, cert) ->
                val certificateIssuer = X500Name(cert.issuerX500Principal.name)
                issuer == certificateIssuer && cert.serialNumber == rid.serialNumber
            }.firstOrNull { entry ->
                return keyStore.getKey(entry.key)
            } ?: throw DecryptionException("Fant ingen gyldige privatsertifikat for dekryptering")
        }
        throw DecryptionException("Fant ikke riktig sertifikat for mottaker: ")
    }
}
