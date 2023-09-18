package no.nav.emottak.melding.process

import io.ktor.server.plugins.BadRequestException
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.Melding
import no.nav.emottak.util.hentKrypteringssertifikat
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.cms.CMSAlgorithm
import org.bouncycastle.cms.CMSEnvelopedDataGenerator
import org.bouncycastle.cms.CMSException
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSTypedData
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

private val kryptering = Kryptering()

fun krypter(byteArray: ByteArray, sertifikat: ByteArray) = kryptering.krypter(byteArray, sertifikat)

fun Melding.krypter(): Melding {
    return this.copy(
        processedPayload = kryptering.krypter(this.processedPayload, this.header),
        kryptert = true
    )
}

class Kryptering {

    fun krypter(byteArray: ByteArray, header: Header): ByteArray {
        val krypteringSertifikat = hentKrypteringssertifikat(header.cpaId, header.to.herID)
        return krypter(byteArray, krypteringSertifikat)
    }
    fun krypter(byteArray: ByteArray, krypteringSertifikat: ByteArray): ByteArray {
        if (byteArray.isEmpty()) {
            throw BadRequestException("Meldingen er tom.")
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
        throw e
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
        throw e
    } catch (e: CMSException) {
        throw e
    }
}

private fun createX509Certificate(certificate: ByteArray): X509Certificate {
    val cf = CertificateFactory.getInstance("X.509")
    return try {
        cf.generateCertificate(ByteArrayInputStream(certificate)) as X509Certificate
    } catch (e: CertificateException) {
        throw e
    }
}