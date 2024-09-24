package no.nav.emottak

import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.EbmsProcessing
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.PartyId
import no.nav.emottak.melding.model.SendInRequest

val validSendInRequest = lazy {
    val fagmelding = ClassLoader.getSystemResourceAsStream("hentpasientliste/hentpasientliste-payload.xml")
    mockSendInRequest("PasientisteForesporsel", "HentPasientliste", fagmelding.readAllBytes(), "123456789")
}

fun mockSendInRequest(service: String, action: String, payload: ByteArray, signedOf: String? = null) = SendInRequest(
    messageId = "321",
    conversationId = "321",
    payloadId = "123",
    addressing = mockAddressing(service, action),
    ebmsProcessing = EbmsProcessing(),
    cpaId = "dummyCpa",
    payload = payload,
    signedOf = signedOf
)

fun mockAddressing(service: String, action: String): Addressing =
    Addressing(
        from = Party(
            partyId = listOf(PartyId("type", "value")),
            role = "dymmyRole"
        ),
        to = Party(
            partyId = listOf(PartyId("type", "value")),
            role = "dummyRole"
        ),
        service = service,
        action = action
    )
