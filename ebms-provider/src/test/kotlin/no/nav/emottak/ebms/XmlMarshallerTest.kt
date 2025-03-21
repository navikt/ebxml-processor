/*
 * This Kotlin source file was generated by the Gradle "init" task.
 */
package no.nav.emottak.ebms

import no.nav.emottak.message.xml.marshal
import no.nav.emottak.message.xml.unmarshal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.xmlsoap.schemas.soap.envelope.Body
import org.xmlsoap.schemas.soap.envelope.Envelope
import org.xmlsoap.schemas.soap.envelope.Header
import org.xmlsoap.schemas.soap.envelope.ObjectFactory
import java.net.URL
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

class XmlMarshallerTest {
    @Test
    fun testSerdeValidateEbxmlMessage() {
        val xmlFile =
            XmlMarshallerTest::class.java.classLoader
                .getResourceAsStream("oppgjørsmelding/2023_08_29T12_56_58_328.xml")

        val envelope = unmarshal(xmlFile.reader().readText(), Envelope::class.java)
        assertTrue(envelope is Envelope)
        assertTrue(envelope.body is Body)
        assertTrue(envelope.header is Header)
        assertTrue((envelope.header as Header).any?.get(0) is MessageHeader)

        val xmlString = marshal(ObjectFactory().createEnvelope(envelope)!!)
        // print(xmlString);
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(
                getResourceURL("envelope.xsd")
            )
            .newValidator()
            .validate(StreamSource(xmlString.byteInputStream()))
    }

    @Test
    fun testSerdeValidateCPA() {
        val xmlFile =
            XmlMarshallerTest::class.java.classLoader.getResourceAsStream("cpa/nav-qass-35065.xml")
        val cpa = unmarshal(xmlFile.reader().readText(), CollaborationProtocolAgreement::class.java)

        assertTrue(cpa is CollaborationProtocolAgreement)
        assertEquals(cpa.cpaid, "nav:qass:35065")
        assertEquals(cpa.version, "2_0b")

        val xmlString = marshal(cpa)
        // print(xmlString);
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(
                getResourceURL("cpp-cpa-2_0.xsd")
            )
            .newValidator()
            .validate(StreamSource(xmlString.byteInputStream()))
    }

    fun getResourceURL(resource: String): URL? {
        return this::class.java.classLoader.getResource(resource)
    }
}
