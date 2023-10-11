package no.nav.emottak.ebms.xml

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

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