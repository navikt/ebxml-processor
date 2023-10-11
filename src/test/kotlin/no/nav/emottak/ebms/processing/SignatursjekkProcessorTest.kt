package no.nav.emottak.ebms.processing

import no.nav.emottak.ebms.model.EbMSAttachment
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.buildEbmMessage
import no.nav.emottak.ebms.xml.getDocumentBuilder
import no.nav.emottak.util.signatur.SignatureException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SignatursjekkProcessorTest {
    @Test
    fun `Validering av signatur`() {

        val dokument = getDocumentBuilder().parse(this::class.java.classLoader
            .getResourceAsStream("oppgjørsmelding/2023_08_29T12_56_58_328.xml"))
        val attachment = this::class.java.classLoader
            .getResourceAsStream("oppgjørsmelding/2023_08_29T12_56_58_328.p7m").readAllBytes()
        val ebMSDocument = EbMSDocument(
            "Test",
            dokument,
            listOf(
                EbMSAttachment(
                    attachment,
                    "application/pkcs7-mime",
                    "3CTGI8UKUKU4.ADHEUDMDCY3Q3@speare.no"
                )
            )
        )
        val signatursjekk = SignatursjekkProcessor(ebMSDocument.dokument, ebMSDocument.buildEbmMessage())
        signatursjekk.process()
    }

    @Test
    fun `Validering av signatur uten attachments feiler`() {

        val dokument = getDocumentBuilder().parse(this::class.java.classLoader
            .getResourceAsStream("oppgjørsmelding/2023_08_29T12_56_58_328.xml"))
        val ebMSDocument = EbMSDocument(
            "Test",
            dokument,
            listOf()
        )
        val signatursjekk = SignatursjekkProcessor(ebMSDocument.dokument, ebMSDocument.buildEbmMessage())
        assertThrows<SignatureException> {
            signatursjekk.process()
        }
    }
}