package no.nav.emottak.cpa

import no.nav.emottak.message.model.Direction.IN
import no.nav.emottak.message.model.Header
import no.nav.emottak.message.model.ValidationRequest
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement

class TestUtil {
    companion object {
        fun createValidTestCPA(): CollaborationProtocolAgreement {
            val testCpaString = String(this::class.java.classLoader.getResource("cpa/nav-qass-35065.xml").readBytes())
            return unmarshal(testCpaString, CollaborationProtocolAgreement::class.java)
        }
    }
}

fun createValidValidationRequest() = ValidationRequest(
    IN,
    messageId = "",
    conversationId = "",
    cpaId = "nav:qass:35065",
    createValidAddressing()
)

fun createValidAddressing() = Addressing(
    to = Party(
        listOf(
            PartyId(
                type = "HER",
                value = "79768"
            )
        ),
        role = "KontrollUtbetaler"
    ),
    from = Party(
        listOf(
            PartyId(
                type = "HER",
                value = "8141253"
            )
        ),
        role = "Behandler"
    ),
    service = "BehandlerKrav",
    action = "OppgjorsMelding"
)

fun createValidTestHeader() = Header(
    messageId = "",
    conversationId = "",
    cpaId = "nav:qass:35065",
    to = Party(
        listOf(
            PartyId(
                type = "HER",
                value = "79768"
            )
        ),
        role = "KontrollUtbetaler"
    ),
    from = Party(
        listOf(
            PartyId(
                type = "HER",
                value = "8141253"
            )
        ),
        role = "Behandler"
    ),
    service = "BehandlerKrav",
    action = "OppgjorsMelding"
)

fun createValidToHERParty() = Party(
    listOf(
        PartyId(
            type = "HER",
            value = "79768"
        )
    ),
    role = "KontrollUtbetaler"
)
fun createValidFromHERParty() = Party(
    listOf(
        PartyId(
            type = "HER",
            value = "8141253"
        )
    ),
    role = "Behandler"
)
