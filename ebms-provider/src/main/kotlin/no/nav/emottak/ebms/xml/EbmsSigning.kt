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
import org.apache.xml.security.c14n.Canonicalizer
import org.apache.xml.security.exceptions.XMLSecurityException
import org.apache.xml.security.signature.XMLSignature
import org.apache.xml.security.transforms.Transforms
import org.apache.xml.security.transforms.params.XPathContainer
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import java.io.ByteArrayOutputStream
import java.security.PublicKey
import java.security.cert.X509Certificate

val ebmsSigning = EbmsSigning()

class EbmsSigning(
    private val keyStore: KeyStoreManager =
        KeyStoreManager(*config().signering.map { it.resolveKeyStoreConfiguration() }.toTypedArray())
) {

    private val canonicalizationMethodAlgorithm = Transforms.TRANSFORM_C14N_OMIT_COMMENTS
    private val SOAP_ENVELOPE_PREFIX = "SOAP-ENV"
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
            signatureAlgorithm = signatureDetails.signatureAlgorithm,
            hashFunction = signatureDetails.hashFunction
        )
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
        document.appendSignature(signature)
        signature.addAttachmentResolver(attachments)
        signature.addDocument(
            "",
            createTransforms(document),
            hashFunction.getOrUseMinimumAllowedAlgorithm()
        )
        attachments.forEach {
            signature.addDocument(
                CID_PREFIX + it.contentId,
                null,
                hashFunction.getOrUseMinimumAllowedAlgorithm()
            )
        }
        signature.addKeyInfo(getPublicCertFromKeyStore(publicCertificate))
        signature.addKeyInfo(publicCertificate)
        signature.sign(keyStore.getPrivateKey(publicCertificate.serialNumber))
    }

    @Throws(XMLSecurityException::class)
    private fun createSignature(document: Document, signatureMethodAlgorithm: String): XMLSignature =
        XMLSignature(
            document,
            null,
            signatureMethodAlgorithm.getOrUseMinimumAllowedAlgorithm(),
            canonicalizationMethodAlgorithm
        )

    private fun getPublicCertFromKeyStore(publicCertificate: X509Certificate): PublicKey =
        keyStore.getCertificate(publicCertificate.serialNumber)?.publicKey
            ?: throw SignatureException(
                "Could not find certificate with " +
                    "subject <${publicCertificate.subjectX500Principal.name}> and " +
                    "issuer <${publicCertificate.issuerX500Principal.name}> in keystore"
            )

    private fun Document.appendSignature(signature: XMLSignature) {
        this.documentElement.getFirstChildElement().appendChild(signature.element)
    }

    private fun XMLSignature.addAttachmentResolver(attachments: List<EbmsAttachment>) {
        this.signedInfo.addResourceResolver(EbMSAttachmentResolver(attachments))
    }

    @Throws(XMLSecurityException::class)
    private fun createTransforms(document: Document): Transforms = Transforms(document).apply {
        addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE)
        addTransform(Transforms.TRANSFORM_XPATH, getXPathTransform(document))
        addTransform(Transforms.TRANSFORM_C14N_OMIT_COMMENTS)
    }

    @Throws(XMLSecurityException::class)
    private fun getXPathTransform(document: Document): NodeList = XPathContainer(document).apply {
        setXPathNamespaceContext(SOAP_ENVELOPE_PREFIX, SOAPConstants.URI_NS_SOAP_1_1_ENVELOPE)
        setXPath(
            (
                "not(" +
                    "ancestor-or-self::node()[@$SOAP_ENVELOPE_PREFIX:actor=\"urn:oasis:names:tc:ebxml-msg:actor:nextMSH\"]" +
                    "|" +
                    "ancestor-or-self::node()[@$SOAP_ENVELOPE_PREFIX:actor=\"${SOAPConstants.URI_SOAP_ACTOR_NEXT}\"]" +
                    ")"
                )
        )
    }.getElementPlusReturns()

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

fun Document.toByteArray(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val canonicalizer = Canonicalizer.getInstance(Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS)
    canonicalizer.canonicalizeSubtree(this, byteArrayOutputStream)
    return byteArrayOutputStream.toByteArray()
}
