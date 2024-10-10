package no.nav.emottak.ebms.validation

import no.nav.emottak.message.model.EbmsAttachment
import org.apache.xml.security.signature.XMLSignatureInput
import org.apache.xml.security.utils.resolver.ResourceResolverContext
import org.apache.xml.security.utils.resolver.ResourceResolverException
import org.apache.xml.security.utils.resolver.ResourceResolverSpi
import java.io.ByteArrayInputStream
import java.io.IOException

const val CID_PREFIX = "cid:"

class EbMSAttachmentResolver(private val attachments: List<EbmsAttachment>) : ResourceResolverSpi() {
    override fun engineCanResolveURI(context: ResourceResolverContext): Boolean {
        return if (context.uriToResolve.startsWith(CID_PREFIX)) {
            attachments.any { att -> context.uriToResolve.substring(CID_PREFIX.length) == att.contentId }
        } else {
            false
        }
    }

    override fun engineResolveURI(context: ResourceResolverContext): XMLSignatureInput {
        if (!context.uriToResolve.startsWith(CID_PREFIX)) {
            throw ResourceResolverException(
                context.uriToResolve,
                arrayOf("Reference URI does not start with $CID_PREFIX"),
                context.uriToResolve,
                context.baseUri
            )
        }
        val result = attachments.firstOrNull {
                att ->
            context.uriToResolve.substring(CID_PREFIX.length) == att.contentId
        } ?: throw ResourceResolverException(
            context.uriToResolve,
            arrayOf("Reference URI ${context.uriToResolve} does not exist!"),
            context.uriToResolve,
            context.baseUri
        )
        return try {
            val input = XMLSignatureInput(ByteArrayInputStream(result.bytes))
            input.sourceURI = context.uriToResolve
            input.mimeType = result.contentType
            input
        } catch (e: IOException) {
            throw ResourceResolverException(e, context.uriToResolve, context.baseUri, context.uriToResolve)
        }
    }
}
