package no.nav.emottak.ebms.xml

import jakarta.xml.soap.SOAPConstants
import no.nav.emottak.crypto.KeyStoreManager
import no.nav.emottak.ebms.configuration.config
import no.nav.emottak.ebms.validation.CID_PREFIX
import no.nav.emottak.ebms.validation.EbMSAttachmentResolver
import no.nav.emottak.message.model.EbmsAttachment
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.util.createX509Certificate
import no.nav.emottak.util.getFirstChildElement
import no.nav.emottak.util.signatur.SignatureException
import org.apache.xml.security.algorithms.MessageDigestAlgorithm
import org.apache.xml.security.exceptions.XMLSecurityException
import org.apache.xml.security.signature.XMLSignature
import org.apache.xml.security.transforms.Transforms
import org.apache.xml.security.transforms.params.XPathContainer
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import java.security.PublicKey
import java.security.cert.X509Certificate

val ebmsSigning = EbmsSigning()

class EbmsSigning(
    private val keyStore: KeyStoreManager =
        KeyStoreManager(*config().signering.map { it.resolveKeyStoreConfiguration() }.toTypedArray())
) {

    private val canonicalizationMethodAlgorithm = Transforms.TRANSFORM_C14N_OMIT_COMMENTS
    private val SOAP_ENVELOPE = SOAPConstants.URI_NS_SOAP_1_1_ENVELOPE
    private val SOAP_NEXT_ACTOR = SOAPConstants.URI_SOAP_ACTOR_NEXT
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        System.setProperty("org.apache.xml.security.ignoreLineBreaks", "true")
        org.apache.xml.security.Init.init()
    }

    fun sign(ebmsDocument: EbmsDocument, signatureDetails: SignatureDetails) {
        sign(
            document = ebmsDocument.document,
            attachments = ebmsDocument.attachments,
            publicCertificate = createX509Certificate(signatureDetails.certificate),
            signatureAlgorithm = signatureDetails.signatureAlgorithm.getOrUseMinimumAllowedAlgorithm(),
            hashFunction = signatureDetails.hashFunction.getOrUseMinimumAllowedAlgorithm()
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
        signature.addKeyInfo(getPublicCertFromKeyStore(publicCertificate))
        signature.addKeyInfo(publicCertificate)
        signature.sign(keyStore.getPrivateKey(publicCertificate.serialNumber))
    }

    private fun getPublicCertFromKeyStore(publicCertificate: X509Certificate): PublicKey =
        keyStore.getCertificate(publicCertificate.serialNumber)?.publicKey
            ?: throw SignatureException(
                "Could not find certificate with " +
                    "subject <${publicCertificate.subjectX500Principal.name}> and " +
                    "issuer <${publicCertificate.issuerX500Principal.name}> in keystore"
            )

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

    private fun String.getOrUseMinimumAllowedAlgorithm(): String = when (this) {
        XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1 -> XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256.also {
            logger.warn("XML Signature Algorithm SHA1 is not allowed, switching to SHA256")
        }
        MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1 -> MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256.also {
            logger.warn("Message Digest Algorithm SHA1 is not allowed, switching to SHA256")
        }
        else -> this
    }
}
