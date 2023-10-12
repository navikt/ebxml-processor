package no.nav.emottak.util

import no.nav.emottak.util.signatur.SignatureException
import org.apache.xml.security.signature.XMLSignature
import org.apache.xml.security.utils.Constants
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.cert.X509Certificate
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

fun retrieveSignatureElement(document: Document): XMLSignature {
    val nodeList: NodeList = document.getElementsByTagNameNS(Constants.SignatureSpecNS, Constants._TAG_SIGNATURE)
    //Regel ID 45, 52
    if (nodeList.length != 1) throw SignatureException("${nodeList.length} signaturer i dokumentet! Skal være nøyaktig 1")
    //Regel ID 363, 42, 32
    return XMLSignature(nodeList.item(0) as Element, Constants.SignatureSpecNS)
}

fun XMLSignature.retrievePublicX509Certificate(): X509Certificate {
    return this.keyInfo.x509Certificate
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

fun Node.getFirstChildElement(): Element {
    var child = this.firstChild
    while (child != null && child.nodeType != Node.ELEMENT_NODE) child = child.nextSibling
    return child as Element
}