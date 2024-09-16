import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.EbmsProcessing
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.PartyId
import no.nav.emottak.melding.model.SendInRequest
import org.junit.jupiter.api.Test

class PasientListeTestTemp {

    @Test
    fun `Build request`() {
        val processedPayload = this::class.java.classLoader
            .getResourceAsStream("hentpasientliste/hentpasientliste-payload.xml")!!.readAllBytes()

        val sendInRequest = SendInRequest(
            messageId = "90e65f73-1622-4c5a-84df-cac621e67858",
            conversationId = "a1d4e6c8-25f0-47f8-ad49-8bad23842ef3",
            payloadId = "cid:attachment-1-90e65f73-1622-4c5a-84df-cac621e67858",
            payload = processedPayload,
            addressing = Addressing(
                to = Party(
                    listOf(
                        PartyId(
                            type = "HER",
                            value = "79768" // NAV
                        )
                    ),
                    "Fastlegeregister"
                ),
                from = Party(
                    listOf(
                        PartyId(
                            type = "HER",
                            value = "8140996"
                        )
                    ),
                    "Fastlege"
                ),
                service = "PasientlisteForesporsel",
                action = "HentPasientliste"
            ),
            ebmsProcessing = EbmsProcessing(
                test = "123"
            )
        )

        val prettyJson = Json {
            prettyPrint = true
            prettyPrintIndent = " "
        }

        val string = prettyJson.encodeToString(sendInRequest)
        print(string)
    }
}
