package no.nav.emottak.payload.helseid.util.util

import net.sf.saxon.xpath.XPathFactoryImpl
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.util.concurrent.ConcurrentHashMap
import javax.xml.namespace.NamespaceContext
import javax.xml.namespace.QName
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpression
import javax.xml.xpath.XPathExpressionException
import javax.xml.xpath.XPathFactory

/**
 * XML XPath stuff.
 */
object XPathUtil {
    private const val INVALID_XPATH_EXPRESSION = "Invalid XPath expression"
    private val xpathCache: MutableMap<Int, MutableMap<String, XPathExpression>> = ConcurrentHashMap()
    private val LOCK = Any()

    /**
     * Gets a normalized value at a given xpath, or null if xpath doesn't exist.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return the normalized value or null.
     */
    fun getNormalizedValueAtPathOrNull(root: Node?, ctx: NamespaceContext?, path: String): String? {
        val s = getAtPathOrNull<String>(root, ctx, path, XPathConstants.STRING)
        return if (s != null) normalizeSpace(s) else null
    }

    /**
     * Gets a list of Nodes at a given xpath.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The list of nodes.
     */
    @JvmStatic
    fun getNodesAtPath(root: Node, ctx: NamespaceContext?, path: String): List<Node> {
        val nl = getAtPath<NodeList>(root, ctx, path, XPathConstants.NODESET)
        val children: MutableList<Node> = mutableListOf()
        if (nl != null) {
            for (i in 0 until nl.length) {
                children.add(nl.item(i))
            }
        }
        return children
    }

    /**
     * Gets a value at a given xpath.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @param qName the qName
     * @param <T> The return type.
     * @return The value
     </T> */
    @Suppress("UNCHECKED_CAST")
    private fun <T> getAtPath(root: Node, ctx: NamespaceContext?, path: String, qName: QName): T? {
        synchronized(LOCK) {
            return try {
                val expr = getXPathExpression(ctx, path)
                expr.evaluate(root, qName) as T
            } catch (e: XPathExpressionException) {
                throw RuntimeException(INVALID_XPATH_EXPRESSION, e)
            }
        }
    }

    /**
     * Gets a value at a given xpath, or null if xpath doesn't exist.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @param qName the qName
     * @param <T> The return type.
     * @return The value or null.
     </T> */
    @Suppress("UNCHECKED_CAST")
    private fun <T> getAtPathOrNull(root: Node?, ctx: NamespaceContext?, path: String, qName: QName): T? =
        synchronized(LOCK) {
            return if (root != null) {
                try {
                    val expr = getXPathExpression(ctx, path)
                    if (expr.evaluate(root, XPathConstants.NODE) == null) null else expr.evaluate(root, qName) as T
                } catch (e: XPathExpressionException) {
                    throw RuntimeException(INVALID_XPATH_EXPRESSION, e)
                }
            } else {
                null
            }
        }

    private fun getXPathExpression(ctx: NamespaceContext?, path: String): XPathExpression {
        val cache = getCache(ctx)
        return cache.getOrPut(path) { computeXPathExpression(ctx, path) }
    }

    private fun computeXPathExpression(ctx: NamespaceContext?, path: String): XPathExpression {
        val factory: XPathFactory = XPathFactoryImpl()
        val xpath = factory.newXPath()
        if (ctx != null) {
            xpath.namespaceContext = ctx
        }
        return try {
            xpath.compile(path)
        } catch (e: XPathExpressionException) {
            throw RuntimeException("failed to compile xpath", e)
        }
    }

    private fun getCache(ctx: NamespaceContext?): MutableMap<String, XPathExpression> {
        val hashcode = ctx?.hashCode() ?: 0
        return xpathCache.getOrPut(hashcode) { mutableMapOf() }
    }

    private fun normalizeSpace(s: String?): String {
        return if (s.isNullOrBlank()) {
            ""
        } else {
            s.replace(MULTIPLE_WHITESPACE_REGEX, " ").trim()
        }
    }

    private val MULTIPLE_WHITESPACE_REGEX = Regex("[ \t\n\\x0B\\f\r]+")
}
