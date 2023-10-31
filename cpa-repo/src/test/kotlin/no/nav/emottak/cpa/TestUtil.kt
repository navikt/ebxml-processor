package no.nav.emottak.cpa

import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.Party
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement

class TestUtil {
    companion object {
        fun createValidTestCPA(): CollaborationProtocolAgreement {
            val testCpaString = String(this::class.java.classLoader.getResource("cpa/nav-qass-35065.xml").readBytes())
            return unmarshal(testCpaString, CollaborationProtocolAgreement::class.java)
        }
    }
}


fun createValidTestHeader() = Header(
    messageId = "",
    conversationId = "",
    cpaId = "nav:qass:35065",
    to = Party(
        partyType = "HER",
        partyId = "79768",
        role = "KontrollUtbetaler"
    ),
    from = Party(
        partyType = "HER",
        partyId = "8141253",
        role = "Behandler"
    ),
    service = "BehandlerKrav",
    action = "OppgjorsMelding"
)

fun createValidToHERParty() = Party(
    partyType = "HER",
    partyId = "79768",
    role = "KontrollUtbetaler"
)
fun createValidFromHERParty() = Party(
    partyType = "HER",
    partyId = "8141253",
    role = "Behandler"
)