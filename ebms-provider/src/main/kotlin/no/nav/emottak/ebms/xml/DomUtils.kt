package no.nav.emottak.ebms.xml

import org.w3c.dom.Document
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

@Throws(ParserConfigurationException::class)
fun getDocumentBuilder(): DocumentBuilder {
    val dbf = DocumentBuilderFactory.newInstance()
    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    dbf.setFeature("http://xml.org/sax/features/external-general-entities", false)
    dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    dbf.isXIncludeAware = false
    dbf.isExpandEntityReferences = false
    dbf.isNamespaceAware = true
    return dbf.newDocumentBuilder()
}

fun Document.asString(): String {
    val domSource = DOMSource(this)
    val transformer: Transformer = TransformerFactory.newInstance().newTransformer()
    val sw = StringWriter()
    val sr = StreamResult(sw)
    transformer.transform(domSource, sr)
    return sw.toString()
}

fun Document.asByteArray(): ByteArray {
    return ByteArrayOutputStream().use {
        TransformerFactory.newInstance().newTransformer()
            .transform(DOMSource(this), StreamResult(it))
        it.toByteArray()
    }
}
