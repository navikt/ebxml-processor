package no.nav.emottak.validering.sertifikat

import no.nav.emottak.crypto.KeyStoreConfig
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.CRLReason
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.X509v2CRLBuilder
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Security
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Test-hjelper for å generere selvsignerte CA-sertifikater og CRL-er signert av dem,
 * slik at CRLChecker sin signatur- og gyldighetssjekk kan testes uten avhengighet til
 * ekte, eksterne CA-er.
 */
object CRLTestFactory {
    init {
        Security.addProvider(BouncyCastleProvider())
    }

    fun generateKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    fun generateSelfSignedCaCertificate(
        subject: X500Name,
        keyPair: KeyPair,
        notBefore: Date = Date(System.currentTimeMillis() - 86_400_000L),
        notAfter: Date = Date(System.currentTimeMillis() + 365L * 86_400_000L)
    ): X509Certificate {
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(System.currentTimeMillis()),
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        return JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
    }

    fun generateCrl(
        issuer: X500Name,
        signingKeyPair: KeyPair,
        thisUpdate: Date,
        nextUpdate: Date,
        revoked: List<Pair<BigInteger, Date>> = emptyList()
    ): X509CRL {
        val builder = X509v2CRLBuilder(issuer, thisUpdate)
        builder.setNextUpdate(nextUpdate)
        revoked.forEach { (serial, revocationDate) ->
            builder.addCRLEntry(serial, revocationDate, CRLReason.privilegeWithdrawn)
        }
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(signingKeyPair.private)
        return JcaX509CRLConverter().setProvider("BC").getCRL(builder.build(signer))
    }

    fun generateLeafCertificate(
        issuer: X500Name,
        subject: X500Name,
        serialNumber: BigInteger,
        caKeyPair: KeyPair,
        subjectKeyPair: KeyPair = generateKeyPair(),
        notBefore: Date = Date(System.currentTimeMillis() - 86_400_000L),
        notAfter: Date = Date(System.currentTimeMillis() + 365L * 86_400_000L)
    ): X509Certificate {
        val builder = JcaX509v3CertificateBuilder(
            issuer,
            serialNumber,
            notBefore,
            notAfter,
            subject,
            subjectKeyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(caKeyPair.private)
        return JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
    }
}

/**
 * KeyStoreConfig som bygger en PKCS12-keystore i minnet fra et gitt sett med
 * sertifikater, til bruk i tester der man ikke ønsker å skrive til disk.
 */
class InMemoryKeyStoreConfig(
    private val trustedCertificates: Map<String, X509Certificate>
) : KeyStoreConfig {
    override val keyStoreType: String = "PKCS12"
    override val keyStorePass: CharArray = "changeit".toCharArray()
    override val keyStoreFile: InputStream
        get() {
            val keyStore = KeyStore.getInstance(keyStoreType)
            keyStore.load(null, null)
            trustedCertificates.forEach { (alias, cert) -> keyStore.setCertificateEntry(alias, cert) }
            val outputStream = ByteArrayOutputStream()
            keyStore.store(outputStream, keyStorePass)
            return ByteArrayInputStream(outputStream.toByteArray())
        }
}
