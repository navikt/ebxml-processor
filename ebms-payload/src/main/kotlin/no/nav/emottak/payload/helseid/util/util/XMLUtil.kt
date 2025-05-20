package no.nav.emottak.payload.helseid.util.util

import no.nav.emottak.payload.helseid.util.lang.ByteUtil
import no.nav.emottak.payload.helseid.util.util.XPathUtil.getNodeAtPath
import no.nav.emottak.payload.helseid.util.util.XPathUtil.getNodesAtPath
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.w3c.dom.bootstrap.DOMImplementationRegistry
import org.w3c.dom.ls.DOMImplementationLS
import org.w3c.dom.ls.LSSerializer
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.Base64
import javax.xml.XMLConstants
import javax.xml.namespace.NamespaceContext
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * XML stuff.
 */
object XMLUtil {
    private val LOG = LoggerFactory.getLogger("no.nav.emottak.payload.helseid.util.XMLUtil")
    private const val FAILED_TO_CREATE = "Failed to create XML document from byte array"
    private val errorHandler = SilentErrorHandler()

    /**
     * Creates an XML Document from a Base64 encoded string.
     * @param encoded The Base64 encoded string.
     * @return The XML Document.
     */
    fun createDocumentFromB64EncodedData(encoded: String): Document =
        createDocument(Base64.getDecoder().decode(ByteUtil.base64Pad(encoded)))

    /**
     * Creates an XML Document from a string
     * @param xml The document.
     * @return The XML Document.
     */
    fun createDocument(xml: String): Document {
        return createDocument(xml.toByteArray())
    }

    /**
     * Creates an XML Document from a (decoded) byte array.
     * @param bytes The document bytes
     * @return The XML Document.
     */
    // XML parsers should not be vulnerable to XXE attacks
    @Suppress("ThrowsCount")
    fun createDocument(bytes: ByteArray?): Document {
        val dbf = DocumentBuilderFactory.newInstance()
        dbf.isExpandEntityReferences = false
        dbf.isNamespaceAware = true
        return try {
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false)
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            dbf.isXIncludeAware = false
            dbf.isExpandEntityReferences = false
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            val db = dbf.newDocumentBuilder()
            db.setErrorHandler(errorHandler)
            ByteArrayInputStream(bytes).use { db.parse(it) }
        } catch (e: ParserConfigurationException) {
            throw RuntimeException(FAILED_TO_CREATE, e)
        } catch (e: IOException) {
            throw RuntimeException(FAILED_TO_CREATE, e)
        } catch (e: SAXException) {
            throw RuntimeException(FAILED_TO_CREATE, e)
        }
    }

    /**
     * inserts a node from another document into rootDocument by replacing the node at the replacePath
     * @param rootDocument the document to insert into
     * @param newChild the node to insert
     * @param ctx The namespace context.
     * @param replacePath xpath expression to locate the node to replace
     * @return the document
     */
    fun replaceChild(rootDocument: Document, newChild: Node, ctx: NamespaceContext?, replacePath: String?): Document {
        val x = getNodeAtPath(rootDocument, ctx, replacePath!!)
        require(x!!.nodeType == newChild.nodeType) { "new and old child must be of same type" }
        val y = rootDocument.importNode(newChild, true)
        if (x.nodeType == Node.ELEMENT_NODE) {
            x.parentNode.replaceChild(y, x)
        } else {
            throw IllegalArgumentException("unsupported node type: " + x.nodeType + " " + x.javaClass.name)
        }
        return rootDocument
    }

    /**
     * Hack to set the Id attributes as proper XML ID attributes because Apache Santuario demands it.
     * This is probably only necessary for the RfHprData node, and could be avoided by loading the document with the
     * correct schema. But since we want this code to be as generic as possible, we do this for all Id attributes just
     * to be on the safe side.
     * @param doc The document.
     */
    fun setIdAttributes(doc: Document) {
        val nodes = getNodesAtPath(doc, null, "//@*[local-name()='Id']")
        for (node in nodes) {
            val parent = getNodeAtPath(node, null, "..") as Element
            if (node.namespaceURI == null) {
                parent.setIdAttribute(node.nodeName, true)
            } else {
                parent.setIdAttributeNS(node.namespaceURI, node.localName, true)
            }
        }
    }

    /**
     * checks if NodeList is not empty.
     * @param nl The nodelist.
     * @return true or false.
     */
    fun isNotEmpty(nl: NodeList?): Boolean {
        return nl != null && nl.length > 0
    }

    /**
     * pretty prints a Node with indentation, no xml declaration and UTF-8 encoding.
     * @param node The node.
     * @return prettyfied XML.
     */
    fun prettyPrint(node: Node?): String {
        return String(getBytes(node, indent = true, omitXMLDeclaration = true, Charsets.UTF_8), Charsets.UTF_8)
    }

    /**
     * pretty prints a Document
     * @param doc The document.
     * @param indent true if we shall indent.
     * @param omitXMLDeclaration true if we shall omit the XML declaration.
     * @param charset The charset.
     * @return prettyfied XML.
     */
    fun prettyPrint(doc: Document?, indent: Boolean, omitXMLDeclaration: Boolean, charset: Charset?): String {
        return String(getBytes(doc, indent, omitXMLDeclaration, charset), charset!!)
    }

    /**
     * gets the bytes in the element the document
     * @param node The node.
     * @param indent true if we shall indent.
     * @param omitXMLDeclaration true if we shall omit the XML declaration.
     * @param charset The charset.
     * @return the document as a byte array
     */
    @Suppress("TooGenericExceptionCaught")
    fun getBytes(node: Node?, indent: Boolean, omitXMLDeclaration: Boolean, charset: Charset?): ByteArray =
        try {
            ByteArrayOutputStream().use { outputStream ->
                val registry = DOMImplementationRegistry.newInstance()
                val impl = registry.getDOMImplementation("LS") as DOMImplementationLS
                val lsWriter = impl.createLSSerializer()
                setParameters(lsWriter, indent, omitXMLDeclaration)
                val output = impl.createLSOutput()
                if (charset != null) {
                    output.encoding = charset.displayName()
                }
                output.byteStream = outputStream
                lsWriter.write(node, output)
                outputStream.toByteArray()
            }
        } catch (e: Exception) {
            LOG.error("failed to serialize", e)
            ByteArray(0)
        }

    private fun setParameters(lsWriter: LSSerializer, indent: Boolean, omitXMLDeclaration: Boolean) {
        val domConfig = lsWriter.domConfig
        domConfig.setParameter("format-pretty-print", indent)
        domConfig.setParameter("xml-declaration", !omitXMLDeclaration)
        lsWriter.newLine = "\n"
    }
}

/**
 * Error handler that doesn't write to stderr (like the default ErrorHandler does)
 */
private class SilentErrorHandler : ErrorHandler {
    override fun warning(exception: SAXParseException) {
        throw exception
    }

    override fun error(exception: SAXParseException) {
        throw exception
    }

    override fun fatalError(exception: SAXParseException) {
        throw exception
    }
}
