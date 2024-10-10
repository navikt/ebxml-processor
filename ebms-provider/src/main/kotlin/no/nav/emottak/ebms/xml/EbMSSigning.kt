package no.nav.emottak.ebms.xml

import jakarta.xml.soap.SOAPConstants
import no.nav.emottak.crypto.FileKeyStoreConfig
import no.nav.emottak.crypto.KeyStore
import no.nav.emottak.crypto.VaultKeyStoreConfig
import no.nav.emottak.crypto.parseVaultJsonObject
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.validation.CID_PREFIX
import no.nav.emottak.ebms.validation.EbMSAttachmentResolver
import no.nav.emottak.message.model.EbmsAttachment
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.util.createX509Certificate
import no.nav.emottak.util.getEnvVar
import no.nav.emottak.util.getFirstChildElement
import no.nav.emottak.util.signatur.SignatureException
import org.apache.xml.security.exceptions.XMLSecurityException
import org.apache.xml.security.signature.XMLSignature
import org.apache.xml.security.transforms.Transforms
import org.apache.xml.security.transforms.params.XPathContainer
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import java.io.FileReader
import java.security.cert.X509Certificate

fun signeringConfig() =
    when (getEnvVar("NAIS_CLUSTER_NAME", "local")) {
        "dev-fss" ->
            // Fixme burde egentlig hente fra dev vault context for å matche prod oppførsel
            FileKeyStoreConfig(
                keyStoreFilePath = getEnvVar("KEYSTORE_FILE"),
                keyStorePass = getEnvVar("KEYSTORE_PWD").toCharArray(),
                keyStoreType = getEnvVar("KEYSTORE_TYPE", "PKCS12")
            )
        "prod-fss" ->
            VaultKeyStoreConfig(
                keyStoreVaultPath = getEnvVar("VIRKSOMHETSSERTIFIKAT_PATH"),
                keyStoreFileResource = getEnvVar("VIRKSOMHETSSERTIFIKAT_SIGNERING"),
                keyStorePassResource = getEnvVar("VIRKSOMHETSSERTIFIKAT_CREDENTIALS")
            )
        else ->
            FileKeyStoreConfig(
                keyStoreFilePath = getEnvVar("KEYSTORE_FILE", "xml/signering_keystore.p12"),
                keyStorePass = FileReader(
                    getEnvVar(
                        "KEYSTORE_PWD_FILE",
                        FileKeyStoreConfig::class.java.classLoader.getResource("credentials-test.json").path.toString()
                    )
                ).readText().parseVaultJsonObject("password").toCharArray(),
                keyStoreType = getEnvVar("KEYSTORE_TYPE", "PKCS12")
            )
    }

val ebMSSigning = EbMSSigning()

class EbMSSigning(private val keyStore: KeyStore = KeyStore(signeringConfig())) {

    private val canonicalizationMethodAlgorithm = Transforms.TRANSFORM_C14N_OMIT_COMMENTS
    private val SOAP_ENVELOPE = SOAPConstants.URI_NS_SOAP_1_1_ENVELOPE
    private val SOAP_NEXT_ACTOR = SOAPConstants.URI_SOAP_ACTOR_NEXT

    fun sign(ebMSDocument: EbMSDocument, signatureDetails: SignatureDetails) {
        sign(
            ebMSDocument.dokument,
            ebMSDocument.attachments,
            createX509Certificate(signatureDetails.certificate),
            signatureDetails.signatureAlgorithm,
            signatureDetails.hashFunction
        )
    }

    @Throws(XMLSecurityException::class)
    private fun createSignature(document: Document, signatureMethodAlgorithm: String): XMLSignature {
        return XMLSignature(document, null, signatureMethodAlgorithm, canonicalizationMethodAlgorithm)
    }

    @Throws(XMLSecurityException::class)
    private fun sign(
        document: Document,
        attachments: List<EbmsAttachment>,
        publicCertificate: X509Certificate,
        signatureAlgorithm: String,
        hashFunction: String
    ) {
        val alias = keyStore.getCertificateAlias(publicCertificate)
            ?: throw SignatureException("Fant ikke sertifikat med subject ${publicCertificate.subjectX500Principal.name} i keystore")
        val keyPair = keyStore.getKeyPair(alias)
        val signature: XMLSignature = createSignature(document, signatureAlgorithm)
        appendSignature(document, signature)
        addAttachmentResolver(signature, attachments)
        attachments.forEach {
            signature.addDocument(
                CID_PREFIX + it.contentId,
                null,
                hashFunction
            )
        }
        signature.addDocument("", createTransforms(document), hashFunction)
        signature.addKeyInfo(keyPair.public)
        signature.addKeyInfo(publicCertificate)
        signature.sign(keyPair.private)
    }

    private fun appendSignature(document: Document, signature: XMLSignature) {
        val soapHeader = document.documentElement.getFirstChildElement()
        soapHeader.appendChild(signature.element)
    }

    private fun addAttachmentResolver(signature: XMLSignature, attachments: List<EbmsAttachment>) {
        val resolver = EbMSAttachmentResolver(attachments)
        signature.signedInfo.addResourceResolver(resolver)
    }

    @Throws(XMLSecurityException::class)
    private fun createTransforms(document: Document): Transforms {
        val result = Transforms(document)
        result.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE)
        result.addTransform(Transforms.TRANSFORM_XPATH, getXPathTransform(document))
        result.addTransform(Transforms.TRANSFORM_C14N_OMIT_COMMENTS)
        return result
    }

    @Throws(XMLSecurityException::class)
    private fun getXPathTransform(document: Document): NodeList {
        val rawPrefix = document.lookupPrefix(SOAP_ENVELOPE)
        val prefix = if (rawPrefix == null) "" else "$rawPrefix:"
        val container = XPathContainer(document)
        container.setXPath(
            (
                "not(ancestor-or-self::node()[@" +
                    prefix +
                    "actor=\"urn:oasis:names:tc:ebxml-msg:actor:nextMSH\"]|ancestor-or-self::node()[@" +
                    prefix +
                    "actor=\"" +
                    SOAP_NEXT_ACTOR
                ) + "\"])"
        )
        return container.getElementPlusReturns()
    }
}
