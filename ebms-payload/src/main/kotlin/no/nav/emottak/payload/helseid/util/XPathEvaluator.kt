package no.nav.emottak.payload.helseid.util

import org.w3c.dom.Node
import org.w3c.dom.NodeList
import javax.xml.namespace.NamespaceContext
import javax.xml.namespace.QName
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpressionException
import javax.xml.xpath.XPathFactory

object XPathEvaluator {

    private val xPathFactory: XPathFactory = XPathFactory.newInstance()

    fun stringAt(node: Node, namespaceContext: NamespaceContext?, xPath: String): String? =
        (evaluate(node, namespaceContext, xPath, XPathConstants.STRING) as String?)
            ?.trim()
            ?.replace(MULTIPLE_WHITESPACE_REGEX, " ")
            ?.ifBlank { null }

    fun nodesAt(node: Node, namespaceContext: NamespaceContext?, xPath: String): List<Node> {
        val nodeList = evaluate(node, namespaceContext, xPath, XPathConstants.NODESET) as NodeList
        val nodes = ArrayList<Node>(nodeList.length)
        for (index in 0 until nodeList.length) nodes.add(nodeList.item(index))
        return nodes
    }

    private fun evaluate(
        contextNode: Node,
        namespaceContext: NamespaceContext?,
        xPathExpression: String,
        expectedResultType: QName
    ) = try {
        val xPath = xPathFactory.newXPath()
        if (namespaceContext != null) {
            xPath.namespaceContext = namespaceContext
        }
        val compiledExpression = xPath.compile(xPathExpression)
        compiledExpression.evaluate(contextNode, expectedResultType)
    } catch (ex: XPathExpressionException) {
        throw RuntimeException("Invalid XPath expression", ex)
    }

    private val MULTIPLE_WHITESPACE_REGEX = Regex("\\s+")
}
