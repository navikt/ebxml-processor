package no.nav.emottak.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream

class XMLUtilTest {

    @Test
    fun `createDocument rejects documents containing a doctype declaration`() {
        val xxe = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
            <foo>&xxe;</foo>
        """.trimIndent()

        assertThrows<SAXException> {
            createDocument(ByteArrayInputStream(xxe.toByteArray()))
        }
    }

    @Test
    fun `createDocument parses ordinary namespace aware xml`() {
        val xml = """<a:foo xmlns:a="urn:test"><a:bar>baz</a:bar></a:foo>"""

        val document = createDocument(ByteArrayInputStream(xml.toByteArray()))

        check(document.documentElement.namespaceURI == "urn:test")
        check(document.getElementsByTagNameNS("urn:test", "bar").length == 1)
    }
}
