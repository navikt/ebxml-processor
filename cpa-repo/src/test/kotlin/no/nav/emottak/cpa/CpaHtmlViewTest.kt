package no.nav.emottak.cpa

import kotlinx.html.html
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.Test
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement

class CpaHtmlViewTest {

    @Test
    fun `render CPA as HTML and open in browser`() {
        val xml = this::class.java.classLoader
            .getResource("cpa/nav-qass-31162.xml")!!
            .readText()
        val cpa = xmlMarshaller.unmarshal(xml, CollaborationProtocolAgreement::class.java)

        val html = createHTML().html { renderCpa(cpa) }

        // Comment in these lines to view the rendering locally in a browser
//        val file = java.io.File.createTempFile("cpa-preview-", ".html")
//        file.writeText(html)
//        println("CPA HTML preview written to: ${file.absolutePath}")
//
//        if (java.awt.Desktop.isDesktopSupported()) {
//            java.awt.Desktop.getDesktop().browse(file.toURI())
//        }
    }
}
