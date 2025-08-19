package no.nav.emottak.payload.crypto

import no.nav.emottak.crypto.FileKeyStoreConfig
import no.nav.emottak.crypto.KeyStoreManager
import no.nav.emottak.crypto.VaultKeyStoreConfig
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.util.createX509Certificate
import no.nav.emottak.util.signatur.SignatureException
import no.nav.emottak.utils.environment.getEnvVar
import no.nav.emottak.utils.vault.parseVaultJsonObject
import org.w3c.dom.Document
import java.io.FileReader
import java.security.Key
import java.security.cert.X509Certificate
import javax.xml.crypto.dsig.Reference
import javax.xml.crypto.dsig.SignedInfo
import javax.xml.crypto.dsig.Transform
import javax.xml.crypto.dsig.XMLSignature
import javax.xml.crypto.dsig.XMLSignatureFactory
import javax.xml.crypto.dsig.dom.DOMSignContext
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec
import javax.xml.crypto.dsig.spec.TransformParameterSpec

fun payloadSigneringConfig() =
    when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
        "dev-fss" ->
            // Fixme burde egentlig hente fra dev vault context for å matche prod oppførsel
            listOf(
                FileKeyStoreConfig(
                    keyStoreFilePath = getEnvVar("KEYSTORE_FILE_SIGN"),
                    keyStorePass = getEnvVar("KEYSTORE_PWD").toCharArray(),
                    keyStoreType = getEnvVar("KEYSTORE_TYPE", "PKCS12")
                )
            )
        "prod-fss" ->
            listOf(
                VaultKeyStoreConfig(
                    keyStoreVaultPath = getEnvVar("VIRKSOMHETSSERTIFIKAT_PATH"),
                    keyStoreFileResource = getEnvVar("VIRKSOMHETSSERTIFIKAT_SIGNERING_2022"),
                    keyStorePassResource = getEnvVar("VIRKSOMHETSSERTIFIKAT_CREDENTIALS_2022")
                ),
                VaultKeyStoreConfig(
                    keyStoreVaultPath = getEnvVar("VIRKSOMHETSSERTIFIKAT_PATH"),
                    keyStoreFileResource = getEnvVar("VIRKSOMHETSSERTIFIKAT_SIGNERING_2025"),
                    keyStorePassResource = getEnvVar("VIRKSOMHETSSERTIFIKAT_CREDENTIALS_2025")
                )
            )
        else ->
            listOf(
                FileKeyStoreConfig(
                    keyStoreFilePath = getEnvVar("KEYSTORE_FILE_SIGN", "keystore/test_keystore2024.p12"),
                    keyStorePass = FileReader(
                        getEnvVar(
                            "KEYSTORE_PWD_FILE",
                            FileKeyStoreConfig::class.java.classLoader.getResource("keystore/credentials-test.json")?.path.toString()
                        )
                    ).readText().parseVaultJsonObject("password").toCharArray(),
                    keyStoreType = getEnvVar("KEYSTORE_TYPE", "PKCS12")
                )
            )
    }

class PayloadSignering(private val keyStore: KeyStoreManager = KeyStoreManager(*payloadSigneringConfig().toTypedArray())) {

    private val digestAlgorithm: String = "http://www.w3.org/2001/04/xmlenc#sha256"
    private val canonicalizationMethod: String = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315"
    private val signatureAlgorithm: String = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"

    private val factory = XMLSignatureFactory.getInstance("DOM")

    fun signerXML(document: Document, signatureDetails: SignatureDetails): Document {
        val signerCertificate: X509Certificate = createX509Certificate(signatureDetails.certificate)
        val signingContext = buildSigningContext(signerCertificate, document)
        val signature = buildXmlSignature(signerCertificate)
        signature.sign(signingContext)
        return document
    }

    private fun buildSigningContext(
        signerCertificate: X509Certificate,
        document: Document
    ): DOMSignContext {
        val alias = keyStore.getCertificateAlias(signerCertificate)
            ?: throw SignatureException("Fant ikke sertifikat med subject ${signerCertificate.subjectX500Principal.name} i keystore")
        val signerKey: Key = keyStore.getKey(alias)
        val signingContext = DOMSignContext(signerKey, document.documentElement)
        return signingContext
    }

    private fun buildXmlSignature(signerCertificate: X509Certificate): XMLSignature {
        val keyInfoFactory = factory.keyInfoFactory
        val x509Content: MutableList<Any?> = ArrayList()
        x509Content.add(signerCertificate)
        val x509data = keyInfoFactory.newX509Data(x509Content)
        val keyInfo = keyInfoFactory.newKeyInfo(listOf(x509data))
        val signature = factory.newXMLSignature(createSignedInfo(), keyInfo)
        return signature
    }

    private fun createSignedInfo(): SignedInfo {
        return factory.newSignedInfo(
            factory.newCanonicalizationMethod(
                canonicalizationMethod,
                null as C14NMethodParameterSpec?
            ),
            factory.newSignatureMethod(signatureAlgorithm, null),
            listOf(createReference())
        )
    }

    private fun createReference(): Reference {
        return factory.newReference(
            "",
            factory.newDigestMethod(digestAlgorithm, null),
            listOf(factory.newTransform(Transform.ENVELOPED, null as TransformParameterSpec?)),
            null,
            null
        )
    }
}
