package no.nav.emottak.fellesformat

import no.nav.emottak.ebms.log
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.EbmsProcessing
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.PartyId
import no.nav.emottak.melding.model.SendInRequest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class FellesFormatWrapperTest {

    @Test
    fun wrapMessageInEIFellesFormat() {
        val processedPayload = this::class.java.classLoader
            .getResourceAsStream("frikort/egenandelforesporsel.xml")!!.readAllBytes()
        val sendInRequest = SendInRequest(
            messageId = "321",
            conversationId = "321",
            payloadId = "123",
            addressing = createAddressing(),
            ebmsProcessing = EbmsProcessing(),
            cpaId = "dummyCpa",
            payload = processedPayload
        )
        val fellesFormat = wrapMessageInEIFellesFormat(sendInRequest)
        Assertions.assertEquals(fellesFormat.mottakenhetBlokk.partnerReferanse, sendInRequest.cpaId)
        log.info(marshal(fellesFormat))
    }
}

fun createAddressing(): Addressing =
    Addressing(
        from = Party(
            partyId = listOf(PartyId("type", "value")),
            role = "Behandler"
        ),
        to = Party(
            partyId = listOf(PartyId("type", "value")),
            role = "Frikortregister"
        ),
        service = "service",
        action = "action"
    )
