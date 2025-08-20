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

object XMLUtil {
    private const val FAILED_TO_CREATE = "Failed to create XML document from byte array"
    private val errorHandler = SilentErrorHandler()

    fun createDocument(xml: String): Document {
        return createDocument(xml.toByteArray())
    }

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
