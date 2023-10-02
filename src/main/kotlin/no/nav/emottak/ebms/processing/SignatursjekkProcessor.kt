package no.nav.emottak.ebms.processing

import no.nav.emottak.ebms.getPublicSigningCertificate
import no.nav.emottak.ebms.model.EbMSAttachment
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.EbMSMessage
import no.nav.emottak.util.createDocument
import no.nav.emottak.util.retrieveSignatureElement
import no.nav.emottak.util.signatur.SignatureException
import org.apache.xml.security.signature.MissingResourceFailureException
import org.apache.xml.security.signature.XMLSignature
import org.apache.xml.security.signature.XMLSignatureInput
import org.apache.xml.security.utils.Constants
import org.apache.xml.security.utils.resolver.ResourceResolverContext
import org.apache.xml.security.utils.resolver.ResourceResolverException
import org.apache.xml.security.utils.resolver.ResourceResolverSpi
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.cert.X509Certificate

class SignatursjekkProcessor(val ebMSDocument: EbMSDocument, ebMSMessage: EbMSMessage): Processor(ebMSMessage) {

    override fun process() {
        validate(ebMSMessage.messageHeader, ebMSDocument.dokument, ebMSMessage.attachments)
    }

    init {
        org.apache.xml.security.Init.init()
    }

    @Throws(SignatureException::class)
    private fun validate(messageHeader: MessageHeader, document: ByteArray, attachments: List<EbMSAttachment>) {
        //TODO Sjekk isNonRepudiation?
        val signatureNode = retrieveSignatureElement(createDocument(ByteArrayInputStream(document)))

        val certificate = getPublicSigningCertificate(messageHeader)

        try {
            if (!verify(
                    certificate,
                    signatureNode as Element,
                    attachments
                )
            ) throw SignatureException("Invalid Signature!")
        } catch (e: MissingResourceFailureException) {
            throw SignatureException("Invalid Signature!", e)
        }
    }

    private fun verify(
        certificate: X509Certificate,
        signatureElement: Element,
        attachments: List<EbMSAttachment>
    ): Boolean {
        val signature = XMLSignature(signatureElement, Constants.SignatureSpecNS)
        val resolver = EbMSAttachmentResolver(attachments)
        signature.addResourceResolver(resolver)
        return signature.checkSignatureValue(certificate)
    }
}

private const val CID = "cid:"

private class EbMSAttachmentResolver(private val attachments: List<EbMSAttachment>) : ResourceResolverSpi() {
    override fun engineCanResolveURI(context: ResourceResolverContext): Boolean {
        return if (context.uriToResolve.startsWith(CID)) attachments.stream()
            .anyMatch { a: EbMSAttachment -> context.uriToResolve.substring(CID.length) == a.contentId } else false
    }

    override fun engineResolveURI(context: ResourceResolverContext): XMLSignatureInput {
        if (!context.uriToResolve.startsWith(CID)) throw ResourceResolverException(
            context.uriToResolve,
            arrayOf<Any>(("Reference URI does not start with '$CID").toString() + "'"),
            context.uriToResolve,
            context.baseUri
        )
        val result = attachments.stream()
            .filter { a: EbMSAttachment -> context.uriToResolve.substring(CID.length) == a.contentId }
            .findFirst()
            .orElseThrow {
                ResourceResolverException(
                    context.uriToResolve, arrayOf<Any>("Reference URI = " + context.uriToResolve + " does not exist!"),
                    context.uriToResolve,
                    context.baseUri
                )
            }
        return try {
            val input = XMLSignatureInput(ByteArrayInputStream(result.dataSource))
            input.sourceURI = context.uriToResolve
            input.mimeType = result.contentType
            input
        } catch (e: IOException) {
            throw ResourceResolverException(e, context.uriToResolve, context.baseUri, context.uriToResolve)
        }
    }
}
