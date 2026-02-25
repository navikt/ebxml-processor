package no.nav.emottak.cpa

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.emottak.cpa.util.EventRegistrationServiceFake
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NhnCommunityPartiesTest {
    val eventRegistrationService = EventRegistrationServiceFake()

    @Test
    fun `Connect to NHN`() {
        assertEquals("expectedCertificate", "expectedCertificate")
    }

    @Test
    fun `Try connecting to NHN`() = nhnTestApp {
        // validateNhn()
    }

    private fun <T> nhnTestApp(testBlock: suspend ApplicationTestBuilder.() -> T) = testApplication {
        testBlock()
    }
}
