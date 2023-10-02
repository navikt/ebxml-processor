package no.nav.emottak.ebms.processing

import no.nav.emottak.ebms.model.EbMSAttachment
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.buildEbmMessage
import no.nav.emottak.util.signatur.SignatureException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SignatursjekkProcessorTest {
    @Test
    fun testSigneringvalideringAvDokument() {

        val dokument = this::class.java.classLoader
            .getResourceAsStream("oppgjørsmelding/2023_08_29T12_56_58_328.xml").readAllBytes()
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
        val signatursjekk = SignatursjekkProcessor(ebMSDocument, ebMSDocument.buildEbmMessage())
        signatursjekk.process()
    }

    @Test
    fun testSigneringvalideringAvDokumentUtenAttachment() {

        val dokument = this::class.java.classLoader
            .getResourceAsStream("oppgjørsmelding/2023_08_29T12_56_58_328.xml").readAllBytes()
        val ebMSDocument = EbMSDocument(
            "Test",
            dokument,
            listOf()
        )
        val signatursjekk = SignatursjekkProcessor(ebMSDocument, ebMSDocument.buildEbmMessage())
        assertThrows<SignatureException> {
            signatursjekk.process()
        }
    }
}