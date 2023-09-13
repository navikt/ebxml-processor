package no.nav.emottak.melding.process

import io.ktor.server.plugins.BadRequestException
import no.nav.emottak.melding.model.Melding
import no.nav.emottak.util.getDekrypteringKey
import no.nav.emottak.util.getPrivateCertificates
import org.bouncycastle.asn1.x500.X500Name
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
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val dekryptering = Dekryptering()

fun dekrypter(byteArray: ByteArray) = dekryptering.dekrypter(byteArray, false)
fun dekrypter(byteArray: ByteArray, isBase64: Boolean) = dekryptering.dekrypter(byteArray, isBase64)

fun Melding.dekrypter(isBase64: Boolean = false): Melding {
    return this.copy(
        processedPayload = dekryptering.dekrypter(this.processedPayload, isBase64)
    )
}

class Dekryptering {

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
        val envelopedData = CMSEnvelopedData(bytes)
        val recipients: RecipientInformationStore = envelopedData.recipientInfos
        for (recipient in recipients.recipients as Collection<RecipientInformation?>) {
            if (recipient is KeyTransRecipientInformation) {
                val key: PrivateKey = getPrivateKeyMatch(recipient)
                return getDeenvelopedContent(recipient, key)
            }
        }
        return byteArrayOf()
    }



    private fun getDeenvelopedContent(recipient: RecipientInformation, key: PrivateKey): ByteArray {
        return recipient.getContent(JceKeyTransEnvelopedRecipient(key)) ?: throw BadRequestException("Meldingen er tom.")
    }

    private fun getPrivateKeyMatch(recipient: RecipientInformation): PrivateKey {
        if (recipient.rid.type == RecipientId.keyTrans) {
            val privateCertificates: Map<String, X509Certificate> = getPrivateCertificates()
            val rid = recipient.rid as KeyTransRecipientId
            val issuer = rid.issuer
            privateCertificates.entries.filter { (key, cert) ->
                val certificateIssuer = X500Name(cert.issuerX500Principal.name)
                issuer == certificateIssuer && cert.serialNumber == rid.serialNumber
            }.firstOrNull { entry ->
                return getDekrypteringKey(entry.key)
            } ?: throw BadRequestException("Fant ingen gyldige privatsertifikat for dekryptering")
        }
        throw BadRequestException("Fant ikke riktig sertifikat for mottaker: ")
    }

}

@OptIn(ExperimentalEncodingApi::class)
internal fun decodeBase64(base64String: ByteArray): ByteArray = Base64.decode(base64String)