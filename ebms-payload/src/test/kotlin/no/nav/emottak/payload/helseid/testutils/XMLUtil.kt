package no.nav.emottak.payload.helseid.testutils

import org.w3c.dom.Document
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * XML stuff.
 */
object XMLUtil {
    private const val FAILED_TO_CREATE = "Failed to create XML document from byte array"
    private val errorHandler = SilentErrorHandler()

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
