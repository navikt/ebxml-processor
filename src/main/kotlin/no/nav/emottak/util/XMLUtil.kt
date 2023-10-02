package no.nav.emottak.util

import no.nav.emottak.util.signatur.KeyValueKeySelector
import no.nav.emottak.util.signatur.SignatureException
import org.apache.xml.security.utils.Constants
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.cert.X509Certificate
import javax.xml.crypto.dsig.XMLSignature
import javax.xml.crypto.dsig.XMLSignatureFactory
import javax.xml.crypto.dsig.dom.DOMValidateContext
import javax.xml.crypto.dsig.keyinfo.X509Data
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal fun createValidateContext(document: Document): DOMValidateContext {
    val domValidateContext = DOMValidateContext(KeyValueKeySelector(), retrieveSignatureElement(document))
    domValidateContext.setProperty("org.jcp.xml.dsig.secureValidation", false)
    return domValidateContext
}


internal fun retrieveXMLSignature(validateContext: DOMValidateContext): XMLSignature {
    val factory = XMLSignatureFactory.getInstance("DOM")
    return factory.unmarshalXMLSignature(validateContext)
}

fun retrieveSignatureElement(document: Document): Node {
    val nodeList: NodeList = document.getElementsByTagNameNS(Constants.SignatureSpecNS, Constants._TAG_SIGNATURE)
    return nodeList.item(0) ?: throw SignatureException("Mangler xmldsig")
}

internal fun retrievePublicCertificateFromSignature(document: Document): X509Certificate {
    return retrievePublicCertificateFromSignature(
        retrieveXMLSignature(createValidateContext(document))
    )
}

private fun retrievePublicCertificateFromSignature(signature: XMLSignature): X509Certificate {
    val x509data = signature.keyInfo.content.filterIsInstance<X509Data>().first()
    return x509data.content.filterIsInstance<X509Certificate>().first()
}


fun createDocument(inputstream: InputStream): Document {
    val dbf = DocumentBuilderFactory.newInstance()
    dbf.isNamespaceAware = true
    return dbf.newDocumentBuilder().parse(inputstream)
}

fun getByteArrayFromDocument(doc: Document): ByteArray {
    val outputStream = ByteArrayOutputStream()
    val xmlSource = DOMSource(doc)
    val result = StreamResult(outputStream)
    TransformerFactory.newInstance().newTransformer().transform(xmlSource, result)
    return outputStream.toByteArray()
}