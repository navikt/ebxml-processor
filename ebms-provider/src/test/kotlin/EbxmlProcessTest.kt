import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.test.dispatcher.*
import no.nav.emottak.ebms.XmlMarshallerTest
import no.nav.emottak.ebms.myApplicationModule
import no.nav.emottak.ebms.validation.ContentTypeRegex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EbxmlProcessTest {

    @Test
    fun myEndpointTest2() = testApplication {
        application { myApplicationModule() }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello, world!", response.bodyAsText())
    }

    @Test
    fun processOrder() = testApplication { application { myApplicationModule() }

        val xmlFile =
            XmlMarshallerTest::class.java.classLoader
                .getResourceAsStream("oppgjørsmelding/2023_08_29T12_56_58_328.xml")
        val response = client.post("/ebxmlMessage") {
            setBody(xmlFile) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello, world!", response.bodyAsText())
    }


    @Test
    fun testEnAckRequestedMelding() {
        val xmlFile =
            XmlMarshallerTest::class.java.classLoader
                .getResourceAsStream("oppgjørsmelding/2023_08_29T12_56_58_328.xml")

    }

}