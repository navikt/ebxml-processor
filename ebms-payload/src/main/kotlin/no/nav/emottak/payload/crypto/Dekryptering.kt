package no.nav.emottak.payload.crypto

import no.nav.emottak.crypto.KeyStoreManager
import no.nav.emottak.payload.configuration.config
import no.nav.emottak.util.decodeBase64
import org.bouncycastle.cms.CMSEnvelopedData
import org.bouncycastle.cms.KeyTransRecipientId
import org.bouncycastle.cms.KeyTransRecipientInformation
import org.bouncycastle.cms.RecipientId
import org.bouncycastle.cms.RecipientInformation
import org.bouncycastle.cms.RecipientInformationStore
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.PrivateKey
import java.security.Security

/*
 *
 * 5.15.1 Dekryptering av vedlegg
 */
class Dekryptering(
    private val keyStore: KeyStoreManager =
        KeyStoreManager(*config().dekryptering.map { it.resolveKeyStoreConfiguration() }.toTypedArray())
) {

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
            val rid = recipient.rid as KeyTransRecipientId
            return keyStore.getPrivateKey(rid.serialNumber)
                ?: throw DecryptionException("Fant ingen gyldige privatsertifikat for dekryptering")
        }
        throw DecryptionException("Fant ikke riktig sertifikat for mottaker: ")
    }
}
