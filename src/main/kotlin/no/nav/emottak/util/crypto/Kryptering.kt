package no.nav.emottak.util.crypto

import no.nav.emottak.util.createX509Certificate
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.cms.CMSAlgorithm
import org.bouncycastle.cms.CMSEnvelopedDataGenerator
import org.bouncycastle.cms.CMSException
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSTypedData
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate

class Kryptering {

    fun krypter(byteArray: ByteArray, krypteringSertifikat: ByteArray): ByteArray {
        if (byteArray.isEmpty()) {
            throw EncryptionException("Meldingen er tom.")
        }
        val sertifikat = createX509Certificate(krypteringSertifikat)
        return krypterDokument(byteArray, sertifikat)
    }

}

private val encryptionAlgorithm: ASN1ObjectIdentifier = CMSAlgorithm.DES_EDE3_CBC
private const val keysize: Int = 168

//private val encryptionAlgorithm: ASN1ObjectIdentifier = CMSAlgorithm.AES256_CBC
//private const val keysize: Int = 256

private fun krypterDokument(doc: ByteArray, certificate: X509Certificate): ByteArray {
    return try {
        krypterDokument(doc, listOf(certificate))
    } catch (e: Exception) {
        throw EncryptionException("Feil ved kryptering av dokument", e)
    }
}

private fun krypterDokument(input: ByteArray, certificates: List<X509Certificate>): ByteArray {

    //val indefiniteLength = false

    return try {
        val dataGenerator = CMSEnvelopedDataGenerator()
        certificates.forEach { certificate ->
            dataGenerator.addRecipientInfoGenerator(JceKeyTransRecipientInfoGenerator(certificate))
        }
        val content: CMSTypedData = CMSProcessableByteArray(input)
        val envelopedData = dataGenerator.generate(
            content,
            JceCMSContentEncryptorBuilder(encryptionAlgorithm, keysize).setProvider(BouncyCastleProvider()).build()
        )
        envelopedData.encoded
    } catch (e: CertificateEncodingException) {
        throw EncryptionException("Feil ved kryptering av dokument", e)
    } catch (e: CMSException) {
        throw EncryptionException("Feil ved kryptering av dokument", e)
    }
}
