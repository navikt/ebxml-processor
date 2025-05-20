package no.nav.emottak.payload.helseid.util.util

import net.sf.saxon.xpath.XPathFactoryImpl
import no.nav.emottak.payload.helseid.util.lang.DateDeserializer
import no.nav.emottak.payload.helseid.util.lang.LocalDateDeserializer
import no.nav.emottak.payload.helseid.util.lang.LocalDateTimeDeserializer
import no.nav.emottak.payload.helseid.util.lang.ZonedDateTimeDeserializer
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.Date
import java.util.Optional
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
@Suppress("TooManyFunctions")
object XPathUtil {
    private const val INVALID_XPATH_EXPRESSION = "Invalid XPath expression"
    private val xpathCache: MutableMap<Int, MutableMap<String, XPathExpression>> = ConcurrentHashMap()
    private val LOCK = Any()

    /**
     * Gets a Node at a given xpath.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The node
     */
    @JvmStatic fun getNodeAtPath(root: Node, ctx: NamespaceContext?, path: String): Node? {
        return getAtPath(root, ctx, path, XPathConstants.NODE)
    }

    /**
     * Counts the nodes at a given xpath.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The count
     */
    fun countAtPath(root: Node, ctx: NamespaceContext?, path: String): Int {
        return getValueAtPath(root, ctx, "count($path)").toInt()
    }

    /**
     * Gets a BigDecimal.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The integer
     */
    fun getBigDecimalAtPath(root: Node?, ctx: NamespaceContext?, path: String): BigDecimal {
        val v = getValueAtPathOrNull(root, ctx, path)
        return if (v == null) BigDecimal.ZERO else bigDecimalWithSameDecimals(v)
    }

    /**
     * Gets a BigDecimal.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The integer
     */
    fun getBigDecimalAtPathOrNull(root: Node?, ctx: NamespaceContext?, path: String): BigDecimal? {
        val v = getValueAtPathOrNull(root, ctx, path)
        return if (v == null) null else bigDecimalWithSameDecimals(v)
    }

    private fun bigDecimalWithSameDecimals(v: String): BigDecimal {
        val big = BigDecimal(v)
        val decimalPoint = v.indexOf('.')
        return if (decimalPoint < 0) {
            big
        } else {
            big.setScale(v.length - decimalPoint - 1, RoundingMode.HALF_UP)
        }
    }

    /**
     * Gets a value at a given xpath.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The value
     */
    fun getValueAtPath(root: Node, ctx: NamespaceContext?, path: String): String {
        return getAtPath(root, ctx, path, XPathConstants.STRING)!!
    }

    /**
     * Gets a normalized value at a given xpath.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The normalized value
     */
    fun getNormalizedValueAtPath(root: Node, ctx: NamespaceContext?, path: String): String {
        val s = getAtPath<String>(root, ctx, path, XPathConstants.STRING)
        return normalizeSpace(s)
    }

    /**
     * Gets an optional value at a given xpath.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return An Optional, with the value or empty
     */
    fun getOptionalValueAtPath(root: Node?, ctx: NamespaceContext?, path: String): Optional<String> {
        return getOptionalAtPath(root, ctx, path, XPathConstants.STRING)
    }

    /**
     * Gets an optional normalized value at a given xpath.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return An Optional, with the value normalized or empty
     */
    fun getOptionalNormalizedValueAtPath(root: Node?, ctx: NamespaceContext?, path: String): Optional<String> {
        val opt = getOptionalAtPath<String>(root, ctx, path, XPathConstants.STRING)
        return opt.map { str: String? -> normalizeSpace(str) }
            .or { opt }
    }

    /**
     * Gets a value at a given xpath, or null if xpath doesn't exist.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return the value or null.
     */
    fun getValueAtPathOrNull(root: Node?, ctx: NamespaceContext?, path: String): String? {
        return getAtPathOrNull<String>(root, ctx, path, XPathConstants.STRING)
    }

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
    @JvmStatic fun getNodesAtPath(root: Node, ctx: NamespaceContext?, path: String): List<Node> {
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
     * Gets a list of Node values at a given xpath.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The list of node values.
     */
    fun getNodeValuesAtPath(root: Node, ctx: NamespaceContext?, path: String): List<String> {
        val nl = getAtPath<NodeList>(root, ctx, path, XPathConstants.NODESET)
        val children: MutableList<String> = mutableListOf()
        if (nl != null) {
            for (i in 0 until nl.length) {
                children.add(nl.item(i).nodeValue)
            }
        }
        return children
    }

    /**
     * Evaluates if an xpath exists.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return True if path exists or false if it doesn't exists.
     */
    fun exists(root: Node, ctx: NamespaceContext?, path: String): Boolean {
        return getAtPath(root, ctx, path, XPathConstants.BOOLEAN)!!
    }

    /**
     * Evaluates if an xpath exists.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return True if path exists or null if it doesn't exists.
     */
    fun existsOrNull(root: Node?, ctx: NamespaceContext?, path: String): Boolean? {
        return getAtPathOrNull<Boolean>(root, ctx, path, XPathConstants.BOOLEAN)
    }

    /**
     * Gets a boolean value.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The boolean value.
     */
    fun getBooleanAtPath(root: Node, ctx: NamespaceContext?, path: String): Boolean {
        return java.lang.Boolean.valueOf(getAtPath<String>(root, ctx, path, XPathConstants.STRING))
    }

    /**
     * Gets a boolean value.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The boolean value or null.
     */
    fun getBooleanAtPathOrNull(root: Node?, ctx: NamespaceContext?, path: String): Boolean? {
        val v = getAtPathOrNull<String>(root, ctx, path, XPathConstants.STRING)
        return if (v == null) null else java.lang.Boolean.valueOf(v)
    }

    /**
     * Gets an integer.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The integer
     */
    fun getIntegerAtPath(root: Node?, ctx: NamespaceContext?, path: String): Int {
        return getOptionalIntegerAtPath(root, ctx, path).orElse(0)
    }

    /**
     * Gets an integer.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The integer
     */
    fun getIntegerAtPathOrNull(root: Node?, ctx: NamespaceContext?, path: String): Int? {
        return getOptionalIntegerAtPath(root, ctx, path).orElse(null)
    }

    /**
     * Gets an integer.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return An optional with the integer or empty
     */
    fun getOptionalIntegerAtPath(root: Node?, ctx: NamespaceContext?, path: String): Optional<Int> {
        val d = getOptionalDoubleAtPath(root, ctx, path)
        return d.map { obj: Double -> obj.toInt() }
    }

    /**
     * Gets a long.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The long.
     */
    fun getLongAtPath(root: Node?, ctx: NamespaceContext?, path: String): Long? {
        return getOptionalLongAtPath(root, ctx, path).orElse(0L)
    }

    /**
     * Gets a long.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The long.
     */
    fun getLongAtPathOrNull(root: Node?, ctx: NamespaceContext?, path: String): Long? {
        return getOptionalLongAtPath(root, ctx, path).orElse(null)
    }

    /**
     * Gets a long.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return An optional with the long or empty
     */
    fun getOptionalLongAtPath(root: Node?, ctx: NamespaceContext?, path: String): Optional<Long> {
        val d = getOptionalDoubleAtPath(root, ctx, path)
        return d.map { obj: Double -> obj.toLong() }
    }

    /**
     * Gets a double.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The double value
     */
    fun getDoubleAtPath(root: Node, ctx: NamespaceContext?, path: String): Double {
        return getAtPath(root, ctx, path, XPathConstants.NUMBER)!!
    }

    /**
     * Gets a double or null if xpath doesn't exists.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The double value or null.
     */
    fun getDoubleAtPathOrNull(root: Node?, ctx: NamespaceContext?, path: String): Double? {
        return getAtPathOrNull<Double>(root, ctx, path, XPathConstants.NUMBER)
    }

    /**
     * Gets an optional double.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return An Optional, with the double value or empty
     */
    fun getOptionalDoubleAtPath(root: Node?, ctx: NamespaceContext?, path: String): Optional<Double> {
        return getOptionalAtPath(root, ctx, path, XPathConstants.NUMBER)
    }

    /**
     * Gets a date with time.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The date
     */
    fun getDateTimeAtPath(root: Node, ctx: NamespaceContext?, path: String): Date? {
        return getDateAtPath(root, ctx, path)
    }

    /**
     * Gets an optional date with time.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return An Optional, with the date or empty
     */
    fun getOptionalDateTimeAtPath(root: Node, ctx: NamespaceContext?, path: String): Optional<Date> {
        return getOptionalDateAtPath(root, ctx, path)
    }

    /**
     * Gets a date with time.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The date
     */
    fun getLocalDateAtPath(root: Node, ctx: NamespaceContext?, path: String): LocalDate? =
        getSomeDateType(root, ctx, path) { LocalDateDeserializer.deserialize(it) }

    /**
     * Gets an optional date with time.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return An Optional, with the date or empty
     */
    fun getOptionalLocalDateAtPath(root: Node, ctx: NamespaceContext?, path: String): Optional<LocalDate> =
        getOptionalSomeDateType(root, ctx, path) { LocalDateDeserializer.deserialize(it) }

    /**
     * Gets a date with time.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The date
     */
    fun getLocalDateTimeAtPath(root: Node, ctx: NamespaceContext?, path: String): LocalDateTime? =
        getSomeDateType(root, ctx, path) { LocalDateTimeDeserializer.deserialize(it) }

    /**
     * Gets an optional date with time.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return An Optional, with the date or empty
     */
    fun getOptionalLocalDateTimeAtPath(root: Node, ctx: NamespaceContext?, path: String): Optional<LocalDateTime> =
        getOptionalSomeDateType(root, ctx, path) { LocalDateTimeDeserializer.deserialize(it) }

    /**
     * Gets a date with time.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The date
     */
    fun getZonedDateTimeAtPath(root: Node, ctx: NamespaceContext?, path: String): ZonedDateTime? =
        getSomeDateType(root, ctx, path) { ZonedDateTimeDeserializer.deserialize(it) }

    /**
     * Gets an optional date with time.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return An Optional, with the date or empty
     */
    fun getOptionalZonedDateTimeAtPath(root: Node, ctx: NamespaceContext?, path: String): Optional<ZonedDateTime> =
        getOptionalSomeDateType(root, ctx, path) { ZonedDateTimeDeserializer.deserialize(it) }

    /**
     * Gets a date.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return The date
     */
    fun getDateAtPath(root: Node, ctx: NamespaceContext?, path: String): Date? =
        getSomeDateType(root, ctx, path) { DateDeserializer.deserialize(it) }

    /**
     * Gets an optional date.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @return An Optional, with the date or empty
     */
    fun getOptionalDateAtPath(root: Node, ctx: NamespaceContext?, path: String): Optional<Date> =
        getOptionalSomeDateType(root, ctx, path) { DateDeserializer.deserialize(it) }

    private fun <T> getSomeDateType(
        root: Node,
        ctx: NamespaceContext?,
        path: String,
        deserializer: (String) -> T?
    ): T? {
        val n = getAtPath<Node>(root, ctx, path, XPathConstants.NODE)
        return if (n == null) null else deserializer(n.nodeValue)
    }

    private fun <T : Any> getOptionalSomeDateType(
        root: Node,
        ctx: NamespaceContext?,
        path: String,
        deserializer: (String) -> T?
    ): Optional<T> {
        val n = getAtPath<Node>(root, ctx, path, XPathConstants.NODE)
        return getOptional(n, deserializer)
    }

    /**
     * Populates a Set of something.
     * @param node The start node to search from. This node is not mandatory.
     * If it is null an empty set will be returned.
     * @param namespaceContext The namespace context.
     * @param path The xpath of the objects that the getter method reads from.
     * @param getter A lambda that reads from node found at path and returns an object of type T.
     * @param <T> The type.
     * @return a populated Set or empty Set.
     </T> */
    fun <T> getSetOrEmpty(
        node: Node?,
        namespaceContext: NamespaceContext?,
        path: String,
        getter: (Node) -> T
    ): Set<T> {
        return getSetOrEmpty(node, namespaceContext, path, false, getter)
    }

    /**
     * Populates a Set of something.
     * @param node The start node to search from. This node is not mandatory.
     * If it is null an empty set will be returned.
     * @param namespaceContext The namespace context.
     * @param path The xpath of the objects that the getter method reads from.
     * @param getter A lambda that reads from node found at path and returns an object of type T.
     * @param <T> The type.
     * @return a populated Set or empty Set.
     </T> */
    fun <T> getSetOrEmpty(
        node: Node?,
        namespaceContext: NamespaceContext?,
        path: String,
        getter: (Node?, NamespaceContext?) -> T
    ): Set<T> {
        return getSetOrEmpty(node, namespaceContext, path, false, getter)
    }

    /**
     * Populates a Set of something.
     * @param node The start node to search from. This node is mandatory.
     * @param namespaceContext The namespace context.
     * @param path The xpath of the objects that the getter method reads from.
     * @param getter A lambda that reads from node found at path and returns an object of type T.
     * @param <T> The type.
     * @return a populated Set.
     </T> */
    fun <T> getSet(node: Node?, namespaceContext: NamespaceContext?, path: String, getter: (Node?) -> T): Set<T> {
        return getSetOrEmpty(node, namespaceContext, path, true, getter)
    }

    /**
     * Populates a Set of something.
     * @param node The start node to search from. This node is mandatory.
     * @param namespaceContext The namespace context.
     * @param path The xpath of the objects that the getter method reads from.
     * @param getter A lambda that reads from node found at path and returns an object of type T.
     * @param <T> The type.
     * @return a populated Set.
     </T> */
    fun <T> getSet(
        node: Node?,
        namespaceContext: NamespaceContext?,
        path: String,
        getter: (Node?, NamespaceContext?) -> T
    ): Set<T> {
        return getSetOrEmpty(node, namespaceContext, path, true, getter)
    }

    /**
     * Populates a Set of something.
     * @param node The start node to search from. This node is mandatory.
     * @param namespaceContext The namespace context.
     * @param path The xpath of the objects that the getter method reads from.
     * @param notNull if true then an XMLException is thrown if node is null,
     * otherwise an empty set is returned if node is null.
     * @param getter A lambda that reads from node found at path and returns an object of type T.
     * @param <T> The type.
     * @return a populated Set.
     </T> */
    fun <T> getSetOrEmpty(
        node: Node?,
        namespaceContext: NamespaceContext?,
        path: String,
        notNull: Boolean,
        getter: (Node) -> T
    ): Set<T> {
        if (node == null) {
            if (notNull) {
                throw RuntimeException("node can't be null, it is mandatory")
            }
            return emptySet()
        }
        return getNodesAtPath(node, namespaceContext, path).map(getter).toSet()
    }

    /**
     * Populates a Set of something.
     * @param node The start node to search from. This node is mandatory.
     * @param namespaceContext The namespace context.
     * @param path The xpath of the objects that the getter method reads from.
     * @param notNull if true then an XMLException is thrown if node is null,
     * otherwise an empty set is returned if node is null.
     * @param getter A lambda that reads from node found at path and returns an object of type T.
     * @param <T> The type.
     * @return a populated Set.
     </T> */
    fun <T> getSetOrEmpty(
        node: Node?,
        namespaceContext: NamespaceContext?,
        path: String,
        notNull: Boolean,
        getter: (Node?, NamespaceContext?) -> T
    ): Set<T> {
        if (node == null) {
            if (notNull) {
                throw RuntimeException("node can't be null, it is mandatory")
            }
            return emptySet()
        }
        return getNodesAtPath(node, namespaceContext, path)
            .map { getter(it, namespaceContext) }
            .toSet()
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
     * Gets an optional value at a given xpath, or Optional.empty if xpath doesn't exist.
     * @param root The root node.
     * @param ctx The namespace context.
     * @param path The xpath.
     * @param qName the qName
     * @param <T> The return type.
     * @return The optional value.
     </T> */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> getOptionalAtPath(
        root: Node?,
        ctx: NamespaceContext?,
        path: String,
        qName: QName
    ): Optional<T> {
        synchronized(LOCK) {
            return if (root != null) {
                try {
                    val expr = getXPathExpression(ctx, path)
                    if (expr.evaluate(root, XPathConstants.NODE) == null) {
                        Optional.empty()
                    } else {
                        Optional.of(expr.evaluate(root, qName) as T)
                    }
                } catch (e: XPathExpressionException) {
                    throw RuntimeException(INVALID_XPATH_EXPRESSION, e)
                }
            } else {
                Optional.empty()
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

    private fun <T : Any> getOptional(n: Node?, deserializer: (String) -> T?): Optional<T> =
        if (n == null) {
            Optional.empty()
        } else {
            val v = deserializer(n.nodeValue)
            if (v == null) Optional.empty() else Optional.of(v)
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
